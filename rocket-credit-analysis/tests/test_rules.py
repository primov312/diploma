from decimal import Decimal

from app.models import FinanceFeatures, HistoryFeatures, ProfileFeatures, ScoreRequest, DecisionStatus, AiStatus
from app.reasons import (
    AI_FALLBACK_RULES_ONLY,
    AMOUNT_ABOVE_PARTNER_CAP,
    AMOUNT_ABOVE_POSSIBLE,
    EVIDENCE_MISSING_FINANCE,
    PARTNER_NO_HISTORY,
    PROFILE_ACCOUNT_NEW,
    ZERO_CAPACITY,
)
from app.rules.affordability import assess_affordability
from app.rules.combiner import combine
from app.rules.history import score_history
from app.rules.profile import score_profile


def test_profile_full_score_for_mature_complete_account(policy):
    r = score_profile(ProfileFeatures(accountAgeMonths=24, profileComplete=True, emailVerified=True), policy)
    assert r.score == 1.0 and r.reasons == []


def test_profile_flags_new_account(policy):
    r = score_profile(ProfileFeatures(accountAgeMonths=1, profileComplete=True, emailVerified=True), policy)
    assert PROFILE_ACCOUNT_NEW in r.reasons and 0 < r.score < 1


def test_history_no_orders_anywhere(policy):
    h = HistoryFeatures(
        partnerOrders12m=0, partnerAvgOrderValue=Decimal("0"), partnerRefundRate=0.0,
        partnerOnTimeRatio=0.0, partnerTenureMonths=0, totalOrders12m=0,
    )
    r = score_history(h, policy)
    assert PARTNER_NO_HISTORY in r.reasons and r.score < 0.2


def test_affordability_formula_and_cap(policy):
    f = FinanceFeatures(monthlyIncome=Decimal("4500"), monthlyExpenses=Decimal("2500"), monthlyObligations=Decimal("300"))
    # disposable 1700 * 3.0 = 5100, capped by partner
    a = assess_affordability(f, Decimal("300"), Decimal("1500"), policy)
    assert a.possible_amount == Decimal("1500.00") and a.within_capacity and a.reasons == []


def test_affordability_zero_capacity_is_explicit(policy):
    f = FinanceFeatures(monthlyIncome=Decimal("1000"), monthlyExpenses=Decimal("900"), monthlyObligations=Decimal("200"))
    a = assess_affordability(f, Decimal("50"), Decimal("1500"), policy)
    assert a.possible_amount == Decimal("0.00") and not a.within_capacity and ZERO_CAPACITY in a.reasons


def test_affordability_over_possible_amount_suggests_lower(policy):
    f = FinanceFeatures(monthlyIncome=Decimal("2200"), monthlyExpenses=Decimal("1900"), monthlyObligations=Decimal("100"))
    a = assess_affordability(f, Decimal("700"), Decimal("1500"), policy)
    assert a.possible_amount == Decimal("600.00") and AMOUNT_ABOVE_POSSIBLE in a.reasons


def test_affordability_over_partner_cap(policy):
    f = FinanceFeatures(monthlyIncome=Decimal("9000"), monthlyExpenses=Decimal("1000"), monthlyObligations=Decimal("0"))
    a = assess_affordability(f, Decimal("2000"), Decimal("1500"), policy)
    assert AMOUNT_ABOVE_PARTNER_CAP in a.reasons and not a.within_capacity


def test_combine_approves_good_customer(policy, good_bundle):
    res = combine(ScoreRequest(**good_bundle), policy)
    assert res.decisionStatus == DecisionStatus.APPROVED
    assert res.score >= policy.thresholds["approve"]
    assert res.possibleAmount == Decimal("1500.00")
    assert res.policyVersion == "rules-v1"
    assert res.aiStatus == AiStatus.NOT_REQUESTED


def test_combine_missing_finance_gives_review(policy, good_bundle):
    good_bundle["finance"] = None
    res = combine(ScoreRequest(**good_bundle), policy)
    assert res.decisionStatus == DecisionStatus.REVIEW
    assert EVIDENCE_MISSING_FINANCE in res.reasons


def test_combine_over_cap_rejects_even_with_good_score(policy, good_bundle):
    good_bundle["requestedAmount"] = "1600.00"
    res = combine(ScoreRequest(**good_bundle), policy)
    assert res.decisionStatus == DecisionStatus.REJECTED
    assert AMOUNT_ABOVE_PARTNER_CAP in res.reasons
    assert res.possibleAmount == Decimal("1500.00")  # suggestion for a new request


def test_combine_ai_requested_without_model_falls_back(policy, good_bundle):
    good_bundle["useAi"] = True
    res = combine(ScoreRequest(**good_bundle), policy)
    assert res.aiRequested and res.aiStatus == AiStatus.UNAVAILABLE
    assert AI_FALLBACK_RULES_ONLY in res.reasons
    assert res.modelVersion is None
