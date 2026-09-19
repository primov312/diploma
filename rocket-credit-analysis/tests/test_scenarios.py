"""Persona scenarios from docs/DEMO_SCENARIOS.md.

The feature values mirror what the Java backend derives from the seeded fixtures
(DbHistoryFeatureProvider) for an observation date shortly after the fixture anchor
2026-09-01, so these tests pin the expected demo outcomes for policy rules-v1.
"""
from decimal import Decimal

import pytest

from app.models import DecisionStatus, ScoreRequest
from app.reasons import (
    AMOUNT_ABOVE_PARTNER_CAP,
    AMOUNT_ABOVE_POSSIBLE,
    PARTNER_REFUND_RATE_HIGH,
    PROFILE_ACCOUNT_NEW,
    PROFILE_INCOMPLETE,
    SCORE_INCONCLUSIVE,
    ZERO_CAPACITY,
)
from app.rules.combiner import combine

OBSERVED = "2026-09-19T10:00:00Z"

AVERY = {
    "profile": {"accountAgeMonths": 30, "profileComplete": True, "emailVerified": True},
    "finance": {"monthlyIncome": "4500.00", "monthlyExpenses": "2500.00", "monthlyObligations": "300.00"},
}
AVERY_MARKETHUB = {"partnerOrders12m": 7, "partnerAvgOrderValue": "137.07", "partnerRefundRate": 0.125,
                   "partnerOnTimeRatio": 1.0, "partnerTenureMonths": 9, "totalOrders12m": 24}

RILEY = {
    "profile": {"accountAgeMonths": 10, "profileComplete": False, "emailVerified": True},
    "finance": {"monthlyIncome": "2500.00", "monthlyExpenses": "1900.00", "monthlyObligations": "300.00"},
}
RILEY_MARKETHUB = {"partnerOrders12m": 5, "partnerAvgOrderValue": "100.20", "partnerRefundRate": 0.1667,
                   "partnerOnTimeRatio": 0.8333, "partnerTenureMonths": 6, "totalOrders12m": 5}

DREW = {
    "profile": {"accountAgeMonths": 1, "profileComplete": False, "emailVerified": False},
    "finance": {"monthlyIncome": "1800.00", "monthlyExpenses": "1700.00", "monthlyObligations": "150.00"},
}
DREW_THREADLY = {"partnerOrders12m": 1, "partnerAvgOrderValue": "129.00", "partnerRefundRate": 0.0,
                 "partnerOnTimeRatio": 0.0, "partnerTenureMonths": 0, "totalOrders12m": 1}

CASEY = {
    "profile": {"accountAgeMonths": 18, "profileComplete": True, "emailVerified": True},
    "finance": {"monthlyIncome": "2200.00", "monthlyExpenses": "1900.00", "monthlyObligations": "100.00"},
}
CASEY_STREAMBOX = {"partnerOrders12m": 10, "partnerAvgOrderValue": "9.99", "partnerRefundRate": 0.0,
                   "partnerOnTimeRatio": 1.0, "partnerTenureMonths": 8, "totalOrders12m": 17}
CASEY_MARKETHUB = {"partnerOrders12m": 4, "partnerAvgOrderValue": "104.00", "partnerRefundRate": 0.0,
                   "partnerOnTimeRatio": 1.0, "partnerTenureMonths": 5, "totalOrders12m": 17}

CAPS = {"streambox": "600.00", "markethub": "1500.00", "threadly": "800.00"}


def request(person, history, partner, amount, use_ai=False):
    return ScoreRequest(requestedAmount=Decimal(amount), partnerCap=Decimal(CAPS[partner]), useAi=use_ai,
                        observedAt=OBSERVED, profile=person["profile"], finance=person["finance"], history=history)


def test_avery_markethub_300_approved(policy):
    r = combine(request(AVERY, AVERY_MARKETHUB, "markethub", "300.00"), policy)
    assert r.decisionStatus == DecisionStatus.APPROVED
    assert r.possibleAmount == Decimal("1500.00")
    assert r.score >= policy.thresholds["approve"]


def test_avery_markethub_1600_rejected_over_cap_with_suggestion(policy):
    r = combine(request(AVERY, AVERY_MARKETHUB, "markethub", "1600.00"), policy)
    assert r.decisionStatus == DecisionStatus.REJECTED
    assert AMOUNT_ABOVE_PARTNER_CAP in r.reasons
    assert r.possibleAmount == Decimal("1500.00")


def test_riley_markethub_250_review(policy):
    r = combine(request(RILEY, RILEY_MARKETHUB, "markethub", "250.00"), policy)
    assert r.decisionStatus == DecisionStatus.REVIEW
    assert policy.thresholds["review"] <= r.score < policy.thresholds["approve"]
    assert {PROFILE_INCOMPLETE, PARTNER_REFUND_RATE_HIGH, SCORE_INCONCLUSIVE} <= set(r.reasons)
    assert r.possibleAmount == Decimal("900.00")


def test_drew_threadly_150_rejected_zero_capacity(policy):
    r = combine(request(DREW, DREW_THREADLY, "threadly", "150.00"), policy)
    assert r.decisionStatus == DecisionStatus.REJECTED
    assert ZERO_CAPACITY in r.reasons and PROFILE_ACCOUNT_NEW in r.reasons
    assert r.possibleAmount == Decimal("0.00")


def test_casey_streambox_300_approved_within_capacity(policy):
    r = combine(request(CASEY, CASEY_STREAMBOX, "streambox", "300.00"), policy)
    assert r.decisionStatus == DecisionStatus.APPROVED
    assert r.possibleAmount == Decimal("600.00")


def test_casey_markethub_700_rejected_with_lower_possible_amount(policy):
    r = combine(request(CASEY, CASEY_MARKETHUB, "markethub", "700.00"), policy)
    assert r.decisionStatus == DecisionStatus.REJECTED
    assert AMOUNT_ABOVE_POSSIBLE in r.reasons
    assert r.possibleAmount == Decimal("600.00")


@pytest.mark.parametrize("amount", ["0", "-1"])
def test_non_positive_amounts_are_invalid(amount):
    with pytest.raises(Exception):
        request(AVERY, AVERY_MARKETHUB, "markethub", amount)


def test_ai_flag_does_not_change_rules_decision_without_model(policy):
    plain = combine(request(AVERY, AVERY_MARKETHUB, "markethub", "300.00"), policy)
    with_ai = combine(request(AVERY, AVERY_MARKETHUB, "markethub", "300.00", use_ai=True), policy)
    assert plain.decisionStatus == with_ai.decisionStatus and plain.score == with_ai.score
    assert with_ai.aiStatus.value == "UNAVAILABLE"
