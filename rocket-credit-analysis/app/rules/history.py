# app/rules/history.py
from __future__ import annotations

from app.models import HistoryFeatures
from app.policy import Policy
from app.reasons import (
    PARTNER_NO_HISTORY,
    PARTNER_ON_TIME_LOW,
    PARTNER_REFUND_RATE_HIGH,
    PARTNER_TOO_FEW_ORDERS,
)
from app.rules.common import RuleResult, clamp01, safe_minmax


def score_history(h: HistoryFeatures, policy: Policy) -> RuleResult:
    """Partner-history rules, carried over from the legacy `score_partner`:
    order count, average order value, on-time ratio, tenure and refunds."""
    cfg = policy.history
    aov_lo, aov_hi = cfg.get("avgOrderValueRange", [10, 500])

    s_orders = safe_minmax(h.partnerOrders12m, 1, cfg.get("ordersFullScore", 12))
    s_aov = safe_minmax(float(h.partnerAvgOrderValue), aov_lo, aov_hi)
    s_refund = 1.0 - clamp01(h.partnerRefundRate)
    s_ontime = clamp01(h.partnerOnTimeRatio)
    s_tenure = safe_minmax(h.partnerTenureMonths, 1, cfg.get("tenureMonthsFullScore", 24))

    score = clamp01(0.28 * s_orders + 0.18 * s_aov + 0.22 * s_ontime + 0.18 * s_tenure + 0.14 * s_refund)

    reasons = []
    if h.partnerOrders12m == 0 and h.totalOrders12m == 0:
        reasons.append(PARTNER_NO_HISTORY)
    elif h.partnerOrders12m < cfg.get("tooFewOrders", 2):
        reasons.append(PARTNER_TOO_FEW_ORDERS)
    if h.partnerOrders12m > 0 and h.partnerOnTimeRatio < cfg.get("onTimeLow", 0.70):
        reasons.append(PARTNER_ON_TIME_LOW)
    if h.partnerRefundRate > cfg.get("refundRateHigh", 0.15):
        reasons.append(PARTNER_REFUND_RATE_HIGH)

    return RuleResult(
        score=score,
        reasons=reasons,
        details={
            "ordersScore": round(s_orders, 4),
            "avgOrderValueScore": round(s_aov, 4),
            "onTimeScore": round(s_ontime, 4),
            "tenureScore": round(s_tenure, 4),
            "refundScore": round(s_refund, 4),
        },
    )
