"""Pure affordability-v2 calculation shared by estimate and decision flows."""
from __future__ import annotations

from decimal import Decimal, ROUND_DOWN

from app.models import AffordabilityInputs, AffordabilityResponse

CENT = Decimal("0.01")
ZERO = Decimal("0")


def calculate_affordability(inputs: AffordabilityInputs, policy_version: str) -> AffordabilityResponse:
    reasons: list[str] = []
    if inputs.monthlyNetIncome is None:
        return _unavailable(policy_version, "INCOME_MISSING")

    if inputs.expenseMode == "ITEMIZED":
        categories = (inputs.housingCost, inputs.groceriesCost, inputs.utilitiesCost,
                      inputs.transportCost, inputs.otherLivingCosts)
        if any(value is None for value in categories):
            return _unavailable(policy_version, "EXPENSES_INCOMPLETE")
        rent = inputs.housingCost or ZERO
        groceries = inputs.groceriesCost or ZERO
        if inputs.referencesEligible:
            if inputs.housingSituation == "RENTING" and inputs.districtRentReference is not None:
                if inputs.districtRentReference > rent:
                    rent = inputs.districtRentReference
                    reasons.append("DISTRICT_RENT_FLOOR_APPLIED")
            if inputs.districtGroceryReference is not None:
                if inputs.districtGroceryReference > groceries:
                    groceries = inputs.districtGroceryReference
                    reasons.append("DISTRICT_GROCERY_FLOOR_APPLIED")
        expenses = rent + groceries + (inputs.utilitiesCost or ZERO) + (inputs.transportCost or ZERO) + (inputs.otherLivingCosts or ZERO)
    else:
        if inputs.legacyLivingExpenses is None:
            return _unavailable(policy_version, "EXPENSES_MISSING")
        expenses = inputs.legacyLivingExpenses
        if inputs.referencesEligible:
            local_floor = (inputs.districtRentReference or ZERO if inputs.housingSituation == "RENTING" else ZERO) + (inputs.districtGroceryReference or ZERO)
            if local_floor > expenses:
                expenses = local_floor
                reasons.append("LOCAL_COST_FLOOR_APPLIED")
        reasons.append("AGGREGATE_EXPENSES")

    if not inputs.referencesEligible:
        reasons.append("LOCAL_COSTS_NOT_APPLIED")
    income = inputs.monthlyNetIncome
    reserve = income * Decimal("0.10")
    disposable = max(ZERO, income - expenses - inputs.monthlyObligations - reserve)
    by_disposable = disposable * Decimal("0.50")
    by_debt_limit = max(ZERO, income * Decimal("0.30") - inputs.monthlyObligations)
    monthly = min(by_disposable, by_debt_limit).quantize(CENT, rounding=ROUND_DOWN)
    base = (monthly * 6).quantize(CENT, rounding=ROUND_DOWN)
    partner = min(base, inputs.partnerCap).quantize(CENT, rounding=ROUND_DOWN)
    breakdown = {
        "income": income.quantize(CENT, rounding=ROUND_DOWN),
        "effectiveExpenses": expenses.quantize(CENT, rounding=ROUND_DOWN),
        "obligations": inputs.monthlyObligations.quantize(CENT, rounding=ROUND_DOWN),
        "reserve": reserve.quantize(CENT, rounding=ROUND_DOWN),
        "paymentFromDisposable": by_disposable.quantize(CENT, rounding=ROUND_DOWN),
        "paymentFromDebtLimit": by_debt_limit.quantize(CENT, rounding=ROUND_DOWN),
    }
    return AffordabilityResponse(
        formulaVersion="affordability-v2", policyVersion=policy_version, termMonths=6,
        baseAmount=base, partnerAmount=partner, monthlyPaymentCapacity=monthly,
        breakdown=breakdown, reasons=list(dict.fromkeys(reasons)),
    )


def _unavailable(policy_version: str, reason: str) -> AffordabilityResponse:
    return AffordabilityResponse(
        formulaVersion="affordability-v2", policyVersion=policy_version, termMonths=6,
        baseAmount=None, partnerAmount=None, monthlyPaymentCapacity=None,
        breakdown={}, reasons=[reason],
    )
