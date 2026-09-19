# app/rules/combiner.py
"""Turn the rule results into one explained decision.

Order of precedence (see docs/DIPLOMA_ARCHITECTURE.md §6):
  1. missing required evidence            -> REVIEW
  2. known zero capacity / above the cap  -> REJECTED
  3. finalScore >= approve threshold      -> APPROVED
  4. finalScore >= review threshold       -> REVIEW
  5. otherwise                             -> REJECTED
"""
from __future__ import annotations

from decimal import Decimal
from typing import Dict, List, Optional

from app.models import (
    AiStatus,
    DecisionStatus,
    FactorResult,
    FinanceFeatures,
    HistoryFeatures,
    ProfileFeatures,
    ScoreRequest,
    ScoreResponse,
)
from app.policy import Policy
from app.ai import AiPrediction
from app.reasons import (
    AI_FALLBACK_RULES_ONLY,
    AI_RISK_ELEVATED,
    EVIDENCE_MISSING_FINANCE,
    EVIDENCE_MISSING_HISTORY,
    EVIDENCE_MISSING_PROFILE,
    SCORE_INCONCLUSIVE,
    SCORE_LOW,
)
from app.rules.affordability import assess_affordability
from app.rules.common import clamp01
from app.rules.history import score_history
from app.rules.profile import score_profile


AI_RISK_ELEVATED_THRESHOLD = 0.5


def combine(req: ScoreRequest, policy: Policy, ai: Optional[AiPrediction] = None) -> ScoreResponse:
    w = policy.weights
    th = policy.thresholds
    reasons: List[str] = []
    factors: Dict[str, FactorResult] = {}
    missing = False

    # --- Profile ---------------------------------------------------------
    if req.profile is None:
        missing = True
        reasons.append(EVIDENCE_MISSING_PROFILE)
        factors["profile"] = FactorResult(score=None, weight=w["profile"], reasons=[EVIDENCE_MISSING_PROFILE])
        profile_score = 0.0
    else:
        r = score_profile(req.profile, policy)
        factors["profile"] = FactorResult(score=r.score, weight=w["profile"], reasons=r.reasons, details=r.details)
        reasons += r.reasons
        profile_score = r.score or 0.0

    # --- History ---------------------------------------------------------
    if req.history is None:
        missing = True
        reasons.append(EVIDENCE_MISSING_HISTORY)
        factors["history"] = FactorResult(score=None, weight=w["history"], reasons=[EVIDENCE_MISSING_HISTORY])
        history_score = 0.0
    else:
        r = score_history(req.history, policy)
        factors["history"] = FactorResult(score=r.score, weight=w["history"], reasons=r.reasons, details=r.details)
        reasons += r.reasons
        history_score = r.score or 0.0

    rules_score = clamp01(w["history"] * history_score + w["profile"] * profile_score)

    # --- AI (optional) ---------------------------------------------------
    ai_status = AiStatus.NOT_REQUESTED
    model_version = None
    contributions: Dict[str, float] = {}
    if req.useAi:
        if ai is None:
            ai_status = AiStatus.UNAVAILABLE
            reasons.append(AI_FALLBACK_RULES_ONLY)
            final_score = rules_score
        else:
            ai_status = AiStatus.APPLIED
            model_version = ai.model_version
            contributions = ai.contributions
            ai_score = clamp01(1.0 - ai.risk)
            final_score = clamp01(w["rules"] * rules_score + w["ai"] * ai_score)
            ai_reasons = [AI_RISK_ELEVATED] if ai.risk >= AI_RISK_ELEVATED_THRESHOLD else []
            reasons += ai_reasons
            top = sorted(ai.contributions.items(), key=lambda kv: -abs(kv[1]))[:4]
            details: Dict[str, float | int | bool | str | None] = {
                "predictedRisk": round(ai.risk, 4), "modelVersion": ai.model_version}
            for name, value in top:
                details["contribution." + name] = value
            factors["ai"] = FactorResult(score=ai_score, weight=w["ai"], reasons=ai_reasons, details=details)
    else:
        final_score = rules_score

    # --- Affordability ---------------------------------------------------
    if req.finance is None:
        missing = True
        reasons.append(EVIDENCE_MISSING_FINANCE)
        factors["affordability"] = FactorResult(score=None, weight=0.0, reasons=[EVIDENCE_MISSING_FINANCE])
        possible = Decimal("0.00")
        within = False
    else:
        a = assess_affordability(req.finance, req.requestedAmount, req.partnerCap, policy)
        factors["affordability"] = FactorResult(score=None, weight=0.0, reasons=a.reasons, details=a.details)
        reasons += a.reasons
        possible = a.possible_amount
        within = a.within_capacity

    # --- Decision --------------------------------------------------------
    if missing:
        status = DecisionStatus.REVIEW
    elif not within:
        status = DecisionStatus.REJECTED
    elif final_score >= th["approve"]:
        status = DecisionStatus.APPROVED
    elif final_score >= th["review"]:
        status = DecisionStatus.REVIEW
        reasons.append(SCORE_INCONCLUSIVE)
    else:
        status = DecisionStatus.REJECTED
        reasons.append(SCORE_LOW)

    return ScoreResponse(
        decisionStatus=status,
        score=round(final_score, 4),
        possibleAmount=possible,
        currency="USD",
        reasons=_dedupe(reasons),
        factors=factors,
        policyVersion=policy.version,
        aiRequested=req.useAi,
        aiStatus=ai_status,
        modelVersion=model_version,
        aiContributions=contributions,
    )


def _dedupe(items: List[str]) -> List[str]:
    seen = set()
    out = []
    for i in items:
        if i not in seen:
            seen.add(i)
            out.append(i)
    return out
