"""Shared feature schema for the AI experiment.

The model sees only normalized, non-identifying features that are available at request
time. Deliberately excluded: customer id, the latent risk used to generate labels, the
label itself, anything dated after the observation, and the rules' own score.
"""
from __future__ import annotations

import math
from typing import Dict, List

FEATURES: List[str] = [
    "accountAgeMonths",
    "profileComplete",
    "emailVerified",
    "partnerOrders12m",
    "logAvgOrderValue",
    "partnerRefundRate",
    "partnerOnTimeRatio",
    "partnerTenureMonths",
    "totalOrders12m",
    "disposableRatio",     # (income - expenses - obligations) / income, clipped to [-1, 1]
    "logIncome",
    "utilization",         # requestedAmount / max(1, disposable * 3), clipped to [0, 3]
]


def featurize(row: Dict) -> List[float]:
    """Map one application-shaped record (same field names as the analysis API) to the model vector."""
    income = float(row["monthlyIncome"])
    disposable = income - float(row["monthlyExpenses"]) - float(row["monthlyObligations"])
    ratio = disposable / income if income > 0 else -1.0
    capacity = max(1.0, disposable * 3.0)
    util = float(row["requestedAmount"]) / capacity
    return [
        float(row["accountAgeMonths"]),
        1.0 if row["profileComplete"] else 0.0,
        1.0 if row["emailVerified"] else 0.0,
        float(row["partnerOrders12m"]),
        math.log1p(float(row["partnerAvgOrderValue"])),
        float(row["partnerRefundRate"]),
        float(row["partnerOnTimeRatio"]),
        float(row["partnerTenureMonths"]),
        float(row["totalOrders12m"]),
        max(-1.0, min(1.0, ratio)),
        math.log1p(max(0.0, income)),
        max(0.0, min(3.0, util)),
    ]
