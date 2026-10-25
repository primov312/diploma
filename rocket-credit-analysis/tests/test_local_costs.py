"""Local costs: Jev picks among amounts found in code, and code checks every pick against the source."""
from app.analyses.local_costs import extract_local_costs
from app.settings import Settings
from tests.jev_support import choice, jev_answers, jev_settings


def local_cost_answer(rent: str = "USD 1000"):
    picks = {"monthlyRent": rent, "monthlyGroceries": "USD 350", "citySalaryMonthly": "USD 2600",
             "district": "District V", "salaryBasis": "NET"}
    return lambda name, q: choice(picks[name], list(q["criteria"]))


def test_fixture_mode_returns_the_source_values():
    result = extract_local_costs("budapest-xi", Settings())
    assert result.status == "EXTRACTED" and result.analysisMode == "FIXTURE" and result.monthlyRent == 820


def test_local_costs_are_picked_from_candidates_found_in_code(monkeypatch):
    calls: list = []
    jev_answers(monkeypatch, local_cost_answer(), calls)

    result = extract_local_costs("budapest-v", jev_settings())

    assert result.status == "EXTRACTED" and result.promptVersion == "local-costs-jev-v1"
    assert (result.monthlyRent, result.monthlyGroceries, result.citySalaryMonthly) == (1000, 350, 2600)
    assert result.evidencePassages[0] == "District V: fictional per-person monthly rent USD 1000;"
    rent_options = calls[0]["body"]["questions"]["monthlyRent"]["criteria"]
    assert list(rent_options) == ["USD 1000", "USD 350", "USD 2600", "none"]


def test_local_costs_reject_wrong_role_none_and_low_confidence(monkeypatch):
    jev_answers(monkeypatch, local_cost_answer(rent="USD 350"))  # groceries amount picked as rent
    assert extract_local_costs("budapest-v", jev_settings()).status == "AI_UNAVAILABLE"
    jev_answers(monkeypatch, local_cost_answer(rent="none"))
    assert extract_local_costs("budapest-v", jev_settings()).status == "AI_UNAVAILABLE"
    jev_answers(monkeypatch, lambda name, q: choice(local_cost_answer()(name, q)["choice"], list(q["criteria"]), p=0.5))
    assert extract_local_costs("budapest-v", jev_settings()).status == "AI_UNAVAILABLE"


def test_http_failure_and_missing_key_report_ai_unavailable(monkeypatch):
    jev_answers(monkeypatch, None, status=503)
    assert extract_local_costs("budapest-v", jev_settings()).status == "AI_UNAVAILABLE"
    missing = extract_local_costs("budapest-v", jev_settings(typesafe_api_key=""))
    assert missing.status == "AI_UNAVAILABLE" and missing.monthlyRent is None
