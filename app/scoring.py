# app/scoring.py
from __future__ import annotations
import math
from dataclasses import dataclass
from typing import Dict, List, Tuple, Optional, Dict, Any

import httpx

from app.models import CreditRequest, CreditResponse
from app.settings import Settings
from app.reasons import (
    KYC_MISSING,
    CONSENT_MISSING,
    AFFORDABILITY_EXCEEDED,
    PARTNER_TOO_FEW_ORDERS,
    PARTNER_ON_TIME_LOW,
    PARTNER_REFUND_RATE_HIGH,
    ROCKET_RECENT_LATE_PAYMENT,
    ROCKET_TOO_MANY_ACTIVE_PLANS,
)

from app.policy import get_current_policy

async def load_features_from_uds(user_id: int, cfg: Settings) -> Dict[str, Any]:
    async with httpx.AsyncClient(timeout=cfg.user_data_timeout) as c:
        r = await c.get(f"{cfg.user_data_url}/user-data", params={"id": user_id})
        r.raise_for_status()
        return r.json()

# ──────────────────────────────────────────────────────────────────────────────
# Audit payload (persist this JSONB)
# ──────────────────────────────────────────────────────────────────────────────
@dataclass
class DecisionAudit:
    approved: bool
    score_float: float   # 0..1
    score_int: int       # 0..100
    final_reason: str
    reasons: List[str]
    factors: Dict[str, dict]   # per-factor subscore + applied weight
    thresholds: Dict[str, float]
    affordability_ok: bool

# ──────────────────────────────────────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────────────────────────────────────
def clamp01(x: float) -> float:
    return max(0.0, min(1.0, float(x)))

def safe_minmax(x: float, lo: float, hi: float) -> float:
    if hi <= lo:
        return 0.5
    return clamp01((x - lo) / (hi - lo))

# ──────────────────────────────────────────────────────────────────────────────
# Eligibility gate (no work-email; Social consent only matters when enabled)
# ──────────────────────────────────────────────────────────────────────────────
def eligibility_gate(user: dict, cfg: Settings) -> Tuple[bool, str | None]:
    if not user.get("kyc_passed", False):
        return False, KYC_MISSING
    if getattr(cfg, "enable_social", False):
        if not user.get("social_consent", False):
            return False, CONSENT_MISSING
    return True, None

# ──────────────────────────────────────────────────────────────────────────────
# Sub-factor scorers
# ──────────────────────────────────────────────────────────────────────────────
def score_partner(u: dict) -> Tuple[float, List[str]]:
    reasons: List[str] = []
    orders_12m   = u.get("partner_orders_12m", 0)
    aov          = u.get("partner_avg_order_value", 0.0)
    refund_rate  = u.get("partner_refund_rate", 0.0)     # 0..1
    ontime_ratio = u.get("partner_ontime_ratio", 0.0)    # 0..1
    tenure_m     = u.get("partner_tenure_months", 0)

    s_orders = safe_minmax(orders_12m, 1, 12)
    s_aov    = safe_minmax(aov, 10, 500)                 # tune per vertical
    s_refund = 1.0 - clamp01(refund_rate)
    s_ontime = clamp01(ontime_ratio)
    s_tenure = safe_minmax(tenure_m, 1, 24)

    score = 0.28*s_orders + 0.18*s_aov + 0.22*s_ontime + 0.18*s_tenure + 0.14*s_refund
    if orders_12m < 2: reasons.append(PARTNER_TOO_FEW_ORDERS)
    if ontime_ratio < 0.7: reasons.append(PARTNER_ON_TIME_LOW)
    if refund_rate > 0.15: reasons.append(PARTNER_REFUND_RATE_HIGH)
    return clamp01(score), reasons

def score_rocket(u: dict) -> Tuple[float, List[str]]:
    reasons: List[str] = []
    ontime = clamp01(u.get("rocket_ontime_ratio", 0.0))
    dpd30  = u.get("rocket_dpd30_12m", 0)  # count of 30+ DPD in last 12m
    active = u.get("rocket_active_plans", 0)
    tenure = safe_minmax(u.get("rocket_tenure_months", 0), 1, 24)

    score = 0.45*ontime + 0.25*tenure \
          + 0.15*(1.0 - safe_minmax(dpd30, 0, 2)) \
          + 0.15*(1.0 - safe_minmax(active, 0, 4))
    if dpd30 >= 1: reasons.append(ROCKET_RECENT_LATE_PAYMENT)
    if active > 3: reasons.append(ROCKET_TOO_MANY_ACTIVE_PLANS)
    return clamp01(score), reasons

def score_amount(u: dict, requested: float, cfg: Settings) -> Tuple[float, List[str]]:
    """
    Amount factor rewards smaller baskets relative to capacity.
    Capacity = max(credit_limit, income * income_multiplier).
    Score = 1 - utilization (clipped to 0..1).
    """
    reasons: List[str] = []
    limit  = float(u.get("credit_limit", 0.0))
    income = float(u.get("income", 0.0))
    income_mult = getattr(cfg, "income_affordability_multiplier", 0.3)  # e.g., 30% of income
    capacity = max(limit, income * income_mult, 0.0)

    if capacity <= 0:
        # Unknown capacity ⇒ be conservative but don’t zero it out
        util = 1.0
    else:
        util = requested / capacity

    score = clamp01(1.0 - util)   # higher util ⇒ lower score

    # Reason hints (final DENIED still comes from affordability gate and thresholds)
    # Add new constant AMOUNT_NEAR_LIMIT in app/reasons.py
    if 0.9 <= util < 1.0:
        reasons.append("AMOUNT_NEAR_LIMIT")

    return score, reasons

# (for later) Credit Bureau & Social — commented out now
# def score_bureau(u: dict) -> Tuple[float, List[str]]: ...
# def score_social(u: dict) -> Tuple[float, List[str], bool]: ...

# ──────────────────────────────────────────────────────────────────────────────
# Main entry
# ──────────────────────────────────────────────────────────────────────────────
async def score_request(req: CreditRequest, cfg: Settings, features_override: Optional[Dict[str, Any]] = None) -> tuple[CreditResponse, DecisionAudit]:
    """
    If features_override is provided, use it instead of calling UDS /user-data.
    Otherwise keep existing behavior.
    """
    # 1) Resolve requested amount safely (supports gateway sending amount OR cartTotal)
    requested = req.cartTotal if getattr(req, "cartTotal", None) is not None else (req.amount or 0.0)

    # 2) Load features (override > UDS)
    u = features_override if features_override is not None else await load_features_from_uds(req.userId, cfg)

    # 3) Eligibility gates
    ok, gate_reason = eligibility_gate(u, cfg)
    if not ok:
        audit = DecisionAudit(
            approved=False, score_float=0.0, score_int=0,
            final_reason=gate_reason or "INELIGIBLE",
            reasons=[gate_reason] if gate_reason else [],
            factors={},
            thresholds={"approve": cfg.approve_threshold, "review": cfg.review_threshold},
            affordability_ok=False,
        )
        return CreditResponse(approved=False, score=0, reason=audit.final_reason), audit

    # 4) Factor scores
    partner_s, partner_r = score_partner(u)
    rocket_s,  rocket_r  = score_rocket(u)
    amount_s,  amount_r  = score_amount(u, requested, cfg)

    # 5) Dynamic policy
    pol = get_current_policy(cfg)
    W = pol.weights
    approve_th = pol.thresholds["approve"]
    review_th  = pol.thresholds["review"]

    # 6) Aggregate score
    s = clamp01(W["rocket"]*rocket_s + W["partner"]*partner_s + W["amount"]*amount_s)

    # 7) Affordability check (use same "capacity" definition as score_amount)
    limit       = float(u.get("credit_limit", 0.0))
    income      = float(u.get("income", 0.0))
    income_mult = getattr(cfg, "income_affordability_multiplier", 0.3)
    capacity    = max(limit, income * income_mult, 0.0)
    affordability_ok = True if capacity <= 0 else (requested <= capacity)

    approved = (s >= approve_th) and affordability_ok
    review   = (not approved) and (s >= review_th) and affordability_ok

    # 8) Reasons & final reason
    reasons: List[str] = []
    if not affordability_ok:
        reasons.append(AFFORDABILITY_EXCEEDED)
    reasons += partner_r + rocket_r + amount_r

    final_reason = ("OK" if approved else ("REVIEW" if review else (reasons[0] if reasons else "SCORE_LOW")))
    score_int = int(math.floor(100.0 * s))

    factors = {
        "rocket":  {"score": rocket_s,  "weight_applied": W["rocket"]},
        "partner": {"score": partner_s, "weight_applied": W["partner"]},
        "amount":  {"score": amount_s,  "weight_applied": W["amount"]},
    }

    audit = DecisionAudit(
        approved=approved,
        score_float=s,
        score_int=score_int,
        final_reason=final_reason,
        reasons=reasons,
        factors=factors,
        thresholds={"approve": approve_th, "review": review_th},
        affordability_ok=affordability_ok,
    )

    public = CreditResponse(approved=approved, score=score_int, reason=final_reason)
    return public, audit