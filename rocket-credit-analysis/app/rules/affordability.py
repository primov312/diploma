# app/rules/affordability.py
from __future__ import annotations

from dataclasses import dataclass, field
from decimal import ROUND_HALF_UP, Decimal
from typing import Dict, List

from app.models import FinanceFeatures
from app.policy import Policy
from app.reasons import AMOUNT_ABOVE_PARTNER_CAP, AMOUNT_ABOVE_POSSIBLE, AMOUNT_NEAR_LIMIT, ZERO_CAPACITY

CENT = Decimal("0.01")


@dataclass
class AffordabilityResult:
    possible_amount: Decimal
    within_capacity: bool
    reasons: List[str] = field(default_factory=list)
    details: Dict[str, float | int | bool | str | None] = field(default_factory=dict)


def assess_affordability(
    f: FinanceFeatures, requested: Decimal, partner_cap: Decimal, policy: Policy
) -> AffordabilityResult:
    """possibleAmount = min(partnerCap, disposableIncome * demoMultiplier).

    The legacy `score_amount` treated capacity <= 0 as "affordable" because the
    check was skipped. Here a known zero capacity is an explicit negative
    result (ZERO_CAPACITY) and is never silently approved.
    """
    multiplier = Decimal(str(policy.affordability["demoMultiplier"]))
    disposable = max(Decimal("0"), f.monthlyIncome - f.monthlyExpenses - f.monthlyObligations)
    capacity = (disposable * multiplier).quantize(CENT, rounding=ROUND_HALF_UP)
    possible = min(partner_cap, capacity).quantize(CENT, rounding=ROUND_HALF_UP)

    reasons: List[str] = []
    within = requested <= possible
    if possible <= 0:
        reasons.append(ZERO_CAPACITY)
        within = False
    elif requested > partner_cap:
        reasons.append(AMOUNT_ABOVE_PARTNER_CAP)
    elif not within:
        reasons.append(AMOUNT_ABOVE_POSSIBLE)
    elif possible > 0 and requested / possible >= Decimal("0.9"):
        reasons.append(AMOUNT_NEAR_LIMIT)

    return AffordabilityResult(
        possible_amount=possible,
        within_capacity=within,
        reasons=reasons,
        details={
            "disposableIncome": float(disposable),
            "capacityBeforeCap": float(capacity),
            "partnerCap": float(partner_cap),
            "demoMultiplier": float(multiplier),
            "utilization": float((requested / possible).quantize(Decimal("0.0001"))) if possible > 0 else None,
        },
    )
