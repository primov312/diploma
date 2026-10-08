from decimal import Decimal
import pytest
from app.models import AffordabilityInputs
from app.rules.affordability_v2 import calculate_affordability
from app.policy import load_policy


def inputs(**updates):
    values = dict(monthlyNetIncome='3000', housingSituation='RENTING', expenseMode='ITEMIZED',
                  housingCost='500', groceriesCost='100', utilitiesCost='50', transportCost='20',
                  otherLivingCosts='30', monthlyObligations='0', districtRentReference='700',
                  districtGroceryReference='200', districtOtherReference='100', referencesEligible=True,
                  partnerCap='10000')
    values.update(updates)
    return AffordabilityInputs(**values)


@pytest.mark.parametrize('housing,eligible,expected', [
    ('RENTING',True,'1070'), ('OWNER',True,'870'), ('FAMILY',True,'870'), ('OTHER',True,'870'),
    ('RENTING',False,'700'),
])
def test_itemized_floors_and_verification(housing,eligible,expected):
    result=calculate_affordability(inputs(housingSituation=housing,referencesEligible=eligible),'rules-v3')
    assert result.formulaVersion=='affordability-v3'
    assert result.breakdown['effectiveExpenses']==Decimal(expected)
    assert ('DISTRICT_OTHER_FLOOR_APPLIED' in result.reasons)==eligible


def test_declared_costs_are_preserved_and_other_is_not_added_twice():
    result=calculate_affordability(inputs(housingCost='900',groceriesCost='300',otherLivingCosts='150'),'rules-v3')
    assert result.breakdown['effectiveExpenses']==Decimal('1420')
    assert 'DISTRICT_OTHER_FLOOR_APPLIED' not in result.reasons


@pytest.mark.parametrize('housing,aggregate,expected', [('RENTING','500','1000'),('OWNER','500','500'),('RENTING','1200','1200')])
def test_aggregate_floor(housing,aggregate,expected):
    request=inputs(housingSituation=housing,expenseMode='AGGREGATE',legacyLivingExpenses=aggregate,
                   housingCost=None,groceriesCost=None,utilitiesCost=None,transportCost=None,otherLivingCosts=None)
    assert calculate_affordability(request,'rules-v3').breakdown['effectiveExpenses']==Decimal(expected)


def test_legacy_v2_formula_ignores_new_other_reference():
    result=calculate_affordability(inputs(),'rules-v2')
    assert result.formulaVersion=='affordability-v2'
    assert result.breakdown['effectiveExpenses']==Decimal('1000')
    assert 'DISTRICT_OTHER_FLOOR_APPLIED' not in result.reasons


def test_missing_income_reports_new_formula_and_policy_weights_stay_equal():
    assert calculate_affordability(inputs(monthlyNetIncome=None),'rules-v3').formulaVersion=='affordability-v3'
    old,new=load_policy('app/policy-v2.json'),load_policy('app/policy-v3.json')
    assert old.weights==new.weights
    assert old.thresholds==new.thresholds
    assert old.profile==new.profile and old.history==new.history


@pytest.mark.parametrize('housing,total',[('RENTING','1000'),('OWNER','300'),('FAMILY','300')])
def test_automatic_budget_uses_resolved_costs_without_declared_amounts(housing,total):
    result=calculate_affordability(inputs(expenseMode='AUTOMATIC',housingSituation=housing),'rules-v4')
    assert result.formulaVersion=='affordability-v4'
    assert result.breakdown['effectiveExpenses']==Decimal(total)
    assert result.reasons==['AUTOMATIC_LIVING_COSTS_APPLIED']


def test_automatic_budget_requires_verified_available_references_and_new_policy():
    assert calculate_affordability(inputs(expenseMode='AUTOMATIC',referencesEligible=False),'rules-v4').baseAmount is None
    assert calculate_affordability(inputs(expenseMode='AUTOMATIC',districtOtherReference=None),'rules-v4').baseAmount is None
    assert calculate_affordability(inputs(expenseMode='AUTOMATIC'),'rules-v3').baseAmount is None
    old,new=load_policy('app/policy-v3.json'),load_policy('app/policy-v4.json')
    assert old.weights==new.weights and old.thresholds==new.thresholds


def test_automatic_supplied_expense_baseline_is_a_floor_and_unverified_fallback():
    high=inputs(expenseMode='AUTOMATIC',legacyLivingExpenses='1200')
    assert calculate_affordability(high,'rules-v4').breakdown['effectiveExpenses']==Decimal('1200')
    unverified=inputs(expenseMode='AUTOMATIC',referencesEligible=False,legacyLivingExpenses='800')
    result=calculate_affordability(unverified,'rules-v4')
    assert result.breakdown['effectiveExpenses']==Decimal('800')
    assert 'SUPPLIED_DEMO_EXPENSE_BASELINE' in result.reasons
