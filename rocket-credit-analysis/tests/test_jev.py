"""Jev (TypeSafe System One) adapters through the real SDK with the HTTP layer mocked, plus an opt-in live check.

The live test runs only when ANALYSIS_TYPESAFE_API_KEY is set:

    ANALYSIS_TYPESAFE_API_KEY=... pytest tests/test_jev.py -k live
"""
import base64
import json
import os
from pathlib import Path

import httpx2
import pytest
import typesafe_sdk

from app import demo_analysis
from app.demo_analysis import ClaimedAddress, analyze_social, extract_address_image, extract_local_costs
from app.settings import Settings

SAMPLE_CARD = Path(__file__).resolve().parents[2] / "demo-repository/src/assets/sample-address-card-riley.png"
RILEY = ClaimedAddress(addressee="Riley Review", countryCode="HU", city="Budapest", districtName="District V",
                       postalCode="1051", street="Minta utca", building="12", unit=None)


def jev_settings(**overrides) -> Settings:
    values = {"demo_provider_mode": "AI", "ai_provider": "JEV", "typesafe_api_key": "test-key"}
    return Settings(**{**values, **overrides})


def noul(p: float) -> dict:
    return {"type": "noul", "noul": p}


def choice(picked: str, options: list[str], p: float = 0.95) -> dict:
    rest = (1 - p) / (len(options) - 1)
    return {"type": "choice", "choice": picked, "confidence": (p - 1 / len(options)) / (1 - 1 / len(options)),
            "probabilities": {o: p if o == picked else rest for o in options}}


def score(probabilities: list[float]) -> dict:
    return {"type": "score", "score": sum(i * p for i, p in enumerate(probabilities)), "confidence": 0.8,
            "legend": {str(i): f"level {i}" for i in range(len(probabilities))},
            "probabilities": {str(i): p for i, p in enumerate(probabilities)}}


def request_json(request: httpx2.Request) -> dict:
    """The SDK sends plain JSON, or multipart with the JSON in a part named `request` when images are attached."""
    if not request.headers["content-type"].startswith("multipart/"):
        return json.loads(request.content)
    part = request.content.split(b'name="request"', 1)[1].split(b"\r\n\r\n", 1)[1]
    return json.loads(part.split(b"\r\n--", 1)[0])


def jev_answers(monkeypatch, answer, calls: list | None = None, status: int = 200):
    """`answer(name, question)` returns the answer JSON for each question the adapter asked."""
    def handler(request: httpx2.Request) -> httpx2.Response:
        body = request_json(request)
        if calls is not None:
            calls.append({"body": body, "headers": dict(request.headers), "raw": request.content})
        if status != 200:
            return httpx2.Response(status, json={"error": {"message": "unavailable"}})
        answers = {name: answer(name, question) for name, question in body["questions"].items()}
        return httpx2.Response(200, json={"model": "jev-1.13.0", "answers": answers, "usage": {"input_tokens": 1, "output_tokens": 1}})

    real = typesafe_sdk.TypeSafeClient
    monkeypatch.setattr(demo_analysis.typesafe_sdk, "TypeSafeClient",
                        lambda **kwargs: real(**kwargs, transport=httpx2.MockTransport(handler)))


def test_repetitive_posts_become_code_written_findings_with_real_ids(monkeypatch):
    calls: list = []
    jev_answers(monkeypatch, lambda name, q: score([0.85, 0.1, 0.05]) if name == "variety"
                else noul({"promo": 0.93, "generic": 0.8, "orders": 0.02}[name.split("_")[0]]), calls)

    report = analyze_social("repetitive", jev_settings())

    assert report.status == "COMPLETE" and report.provider == "TYPESAFE" and report.modelVersion == "jev-1.13.0"
    assert report.promptVersion == "social-jev-v1"
    by_label = {f.label: f for f in report.findings}
    assert set(by_label) == {"PROMOTIONAL_CONTENT", "GENERIC_COMMENTS", "LOW_TOPIC_VARIETY"}
    assert by_label["PROMOTIONAL_CONTENT"].evidenceIds == [f"post-{n:02d}" for n in (2, 3, 4, 5, 6, 7)]
    assert by_label["PROMOTIONAL_CONTENT"].confidence == "HIGH" and by_label["GENERIC_COMMENTS"].confidence == "MEDIUM"
    assert report.summary.startswith("The sample shows patterns")
    body = calls[0]["body"]
    assert body["model"] == "jev-1.13.0" and calls[0]["headers"]["authorization"] == "Bearer test-key"
    assert len(body["questions"]) == 6 * 3 + 1 and body["questions"]["variety"]["type"] == "score"


def test_post_text_never_enters_a_question(monkeypatch):
    calls: list = []
    jev_answers(monkeypatch, lambda name, q: noul(0.97 if name.startswith("orders") else 0.03), calls)

    report = analyze_social("injection", jev_settings())

    questions = json.dumps(calls[0]["body"]["questions"])
    assert "Ignore all prior instructions" not in questions
    assert "Ignore all prior instructions" in calls[0]["body"]["state"]["posts"][0]["text"]
    labels = [f.label for f in report.findings]
    assert labels == ["EMBEDDED_INSTRUCTIONS", "INCONCLUSIVE"]  # one post: no variety question
    assert report.summary.endswith("Orders written inside posts were not followed.")


def test_varied_posts(monkeypatch):
    jev_answers(monkeypatch, lambda name, q: score([0.02, 0.08, 0.9]) if name == "variety" else noul(0.05))
    report = analyze_social("ordinary", jev_settings())
    assert [(f.label, f.confidence) for f in report.findings] == [("VARIED_TOPICS", "HIGH")]
    assert report.findings[0].evidenceIds == ["post-01", "post-02", "post-03"]


def test_http_failure_and_missing_key_fall_back(monkeypatch):
    jev_answers(monkeypatch, None, status=401)
    failed = analyze_social("ordinary", jev_settings())
    assert failed.status == "AI_UNAVAILABLE" and failed.findings == [] and failed.metrics["postCount"] == 3
    assert extract_local_costs("budapest-v", jev_settings()).status == "AI_UNAVAILABLE"
    assert analyze_social("ordinary", jev_settings(typesafe_api_key="")).summary.startswith("No AI provider key")


def local_cost_answer(rent: str = "USD 1000"):
    picks = {"monthlyRent": rent, "monthlyGroceries": "USD 350", "citySalaryMonthly": "USD 2600",
             "district": "District V", "salaryBasis": "NET"}
    return lambda name, q: choice(picks[name], list(q["criteria"]))


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


def card() -> str:
    return base64.b64encode(SAMPLE_CARD.read_bytes()).decode()


def test_address_card_checks_each_claimed_field_against_the_image(monkeypatch):
    calls: list = []
    jev_answers(monkeypatch, lambda name, q: choice("ADDRESS_CARD", list(q["criteria"])) if name == "documentType"
                else noul(0.1 if name == "postalCode" else 0.97), calls)

    result = extract_address_image(card(), "image/png", jev_settings(), RILEY)

    assert result.status == "EXTRACTED" and result.provider == "TYPESAFE" and result.promptVersion == "address-jev-v1"
    assert result.documentType == "ADDRESS_CARD" and result.street == "Minta utca"
    assert result.postalCode is None and result.fieldChecks["postalCode"] == 0.1  # not confirmed: not echoed back
    assert result.fieldChecks["unit"] == 0.97 and result.unit is None
    request = calls[0]
    assert request["headers"]["typesafe-danger-zone-images"] == "true"
    assert request["body"]["state"]["claimed"]["street"] == "Minta utca"
    image_ref = request["body"]["state"]["card"]
    assert request["body"]["assets"][image_ref]["type"] == "image" and SAMPLE_CARD.read_bytes() in request["raw"]
    assert "no flat, floor or door" in request["body"]["questions"]["unit"]["instructions"]


def test_address_needs_a_claim_in_jev_mode():
    with pytest.raises(ValueError):
        extract_address_image(card(), "image/png", jev_settings())


LIVE_KEY = os.environ.get("ANALYSIS_TYPESAFE_API_KEY", "")


@pytest.mark.skipif(not LIVE_KEY, reason="set ANALYSIS_TYPESAFE_API_KEY to call the real TypeSafe API")
def test_live_jev_social_local_costs_and_address():
    settings = Settings(demo_provider_mode="AI", ai_provider="JEV")
    repetitive = analyze_social("repetitive", settings)
    assert repetitive.status == "COMPLETE", "social analysis fell back; see the 'ai call failed' log line"
    assert {"PROMOTIONAL_CONTENT", "LOW_TOPIC_VARIETY"} & {f.label for f in repetitive.findings}

    assert extract_local_costs("budapest-xi", settings).status == "EXTRACTED"

    result = extract_address_image(card(), "image/png", settings, RILEY)
    assert result.status == "EXTRACTED" and result.documentType == "ADDRESS_CARD"
    assert all(p >= demo_analysis.JEV_CONFIRM for p in result.fieldChecks.values()), result.fieldChecks
    wrong = extract_address_image(card(), "image/png", settings, RILEY.model_copy(update={"postalCode": "1239"}))
    assert wrong.fieldChecks["postalCode"] <= 0.15, wrong.fieldChecks
