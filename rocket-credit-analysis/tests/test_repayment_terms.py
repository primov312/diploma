import json
from decimal import Decimal
from pathlib import Path
import pytest
from pydantic import ValidationError
from app.models import RepaymentTerms, AffordabilityInputs, ScoreRequest
from app.rules.repayment_terms import maximum_payment, affordable_principal
from app.rules.affordability_v2 import calculate_affordability
from app.rules.combiner import combine
from app.policy import load_policy


def terms(unit="MONTH", count=6, rate="0.12"):
    return RepaymentTerms(termUnit=unit, installmentCount=count, annualRate=rate, pricingVersion="annuity-v1")


def test_shared_golden_cases():
    path = Path(__file__).resolve().parents[2] / "research/fixtures/repayment-golden.json"
    for case in json.loads(path.read_text()):
        t = RepaymentTerms(**{k: case[k] for k in ("termUnit", "installmentCount", "annualRate", "pricingVersion")})
        assert maximum_payment(Decimal(case["principal"]), t) == Decimal(case["maximumPayment"])


@pytest.mark.parametrize("unit,count", [("WEEK", 0), ("WEEK", 4), ("MONTH", 25), ("MONTH", 1.5)])
def test_reject_invalid_terms(unit, count):
    with pytest.raises(ValidationError):
        terms(unit, count)


def test_principal_capacity_respects_rounded_final_payment():
    for unit, limit in [("WEEK", 3), ("MONTH", 24)]:
        for count in range(1, limit + 1):
            t = terms(unit, count)
            principal = affordable_principal(Decimal("100"), t)
            capacity = Decimal("100") * 12 / 52 if unit == "WEEK" else Decimal("100")
            assert maximum_payment(principal, t) <= capacity
    assert affordable_principal(Decimal("100"), terms(count=12)) > affordable_principal(Decimal("100"), terms(count=6))


def test_term_scoring_preserves_legacy_and_accounts_for_debt():
    inputs = AffordabilityInputs(monthlyNetIncome="4500", housingSituation="OTHER", expenseMode="AGGREGATE", legacyLivingExpenses="2500", monthlyObligations="300", partnerCap="100000")
    legacy = calculate_affordability(inputs, "rules-v4")
    monthly = calculate_affordability(inputs, "rules-v4", terms(count=6))
    weekly = calculate_affordability(inputs, "rules-v4", terms("WEEK", 3))
    indebted = calculate_affordability(inputs.model_copy(update={"monthlyObligations": Decimal("500")}), "rules-v4", terms(count=6))
    assert legacy.baseAmount > monthly.baseAmount > weekly.baseAmount
    assert monthly.baseAmount > indebted.baseAmount
    assert monthly.formulaVersion.endswith("credit-v1")
    assert not legacy.formulaVersion.endswith("credit-v1")


def test_short_term_can_reject_amount_approved_for_longer_term(good_bundle):
    policy = load_policy("app/policy.json")
    request = ScoreRequest(**good_bundle, repaymentTerms=terms("WEEK", 1))
    # Supply v4 inputs explicitly; older policy fixtures remain unchanged.
    request = request.model_copy(update={"affordability": AffordabilityInputs(monthlyNetIncome="4500", housingSituation="OTHER", expenseMode="AGGREGATE", legacyLivingExpenses="2500", monthlyObligations="300", partnerCap="1500")})
    assert combine(request, policy).decisionStatus == "REJECTED"
    assert combine(request.model_copy(update={"repaymentTerms": terms(count=6)}), policy).decisionStatus == "APPROVED"
