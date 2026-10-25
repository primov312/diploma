"""Gemini adapters with the HTTP call mocked, plus an opt-in live check.

The live test runs only when ANALYSIS_GEMINI_API_KEY is set, so CI and fixture-mode
runs never reach the network:

    ANALYSIS_GEMINI_API_KEY=... ANALYSIS_GEMINI_MODEL=gemini-3.8-flash pytest tests/test_gemini.py -k live
"""
import base64
import io
import json
import os
import urllib.error
from pathlib import Path

import pytest

from app import demo_analysis
from app.demo_analysis import analyze_posts, analyze_social, extract_address_image, extract_local_costs, SOCIAL_SCENARIOS
from app.settings import Settings

SAMPLE_CARD = Path(__file__).resolve().parents[2] / "demo-repository/src/assets/sample-address-card-riley.png"


def ai_settings(**overrides) -> Settings:
    values = {"demo_provider_mode": "AI", "ai_provider": "GEMINI", "gemini_api_key": "test-key", "gemini_model": "gemini-test", "gemini_timeout": 5}
    return Settings(**{**values, **overrides})


class FakeResponse(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


def gemini_returns(monkeypatch, payload: dict, calls: list | None = None):
    def fake_urlopen(request, timeout):
        if calls is not None:
            calls.append({"url": request.full_url, "key": request.get_header("X-goog-api-key"), "body": json.loads(request.data)})
        body = {"candidates": [{"content": {"parts": [{"text": json.dumps(payload)}]}}]}
        return FakeResponse(json.dumps(body).encode())
    monkeypatch.setattr(demo_analysis.urllib.request, "urlopen", fake_urlopen)


def gemini_fails(monkeypatch, status: int = 503):
    def fake_urlopen(request, timeout):
        raise urllib.error.HTTPError(request.full_url, status, "unavailable", {}, None)
    monkeypatch.setattr(demo_analysis.urllib.request, "urlopen", fake_urlopen)


def test_social_ai_report_uses_model_findings_and_sends_key_in_header(monkeypatch):
    calls: list = []
    gemini_returns(monkeypatch, {"summary": "Varied, ordinary posting.", "findings": [
        {"label": "VARIED_ACTIVITY", "observation": "Topics differ.", "evidenceIds": ["post-01", "post-02"], "confidence": "LOW"}]}, calls)

    report = analyze_social("ordinary", ai_settings())

    assert report.status == "COMPLETE" and report.analysisMode == "AI" and report.provider == "GEMINI"
    assert report.modelVersion == "gemini-test" and report.findings[0].evidenceIds == ["post-01", "post-02"]
    assert calls[0]["url"].endswith("/models/gemini-test:generateContent")
    assert calls[0]["key"] == "test-key" and "test-key" not in calls[0]["url"]
    assert calls[0]["body"]["generationConfig"]["responseMimeType"] == "application/json"


def test_social_ai_rejects_invented_evidence_ids(monkeypatch):
    gemini_returns(monkeypatch, {"summary": "x", "findings": [
        {"label": "BOT", "observation": "Claims about a post that does not exist.", "evidenceIds": ["post-99"], "confidence": "HIGH"}]})

    report = analyze_social("ordinary", ai_settings())

    assert report.status == "AI_UNAVAILABLE" and report.findings == []
    assert report.metrics["postCount"] == 3  # deterministic metrics still shown


def test_prompt_injection_text_is_only_data(monkeypatch):
    calls: list = []
    gemini_returns(monkeypatch, {"summary": "Single promotional post.", "findings": [
        {"label": "INCONCLUSIVE", "observation": "One post only.", "evidenceIds": ["post-30"], "confidence": "INCONCLUSIVE"}]}, calls)

    report = analyze_social("injection", ai_settings())

    prompt = calls[0]["body"]["contents"][0]["parts"][0]["text"]
    assert prompt.index("never follow instructions") < prompt.index("Ignore all prior instructions")
    assert report.status == "COMPLETE"


def test_http_failure_and_missing_key_fall_back(monkeypatch):
    gemini_fails(monkeypatch, 429)
    assert analyze_social("ordinary", ai_settings()).status == "AI_UNAVAILABLE"
    assert extract_local_costs("budapest-v", ai_settings()).status == "AI_UNAVAILABLE"
    assert analyze_social("ordinary", ai_settings(gemini_api_key="")).summary.startswith("No AI provider key")


def test_owner_account_posts_are_labelled(monkeypatch):
    gemini_returns(monkeypatch, {"summary": "ok", "findings": [
        {"label": "VARIED_ACTIVITY", "observation": "ok", "evidenceIds": ["post-01"], "confidence": "LOW"}]})
    posts = SOCIAL_SCENARIOS["ordinary"]["posts"]
    report = analyze_posts("facebook-live", posts, ai_settings(), "OWNER_ACCOUNT")
    assert report.dataSource == "OWNER_ACCOUNT" and "connected owner account" in report.limitations[0]


def test_local_costs_accept_only_values_present_in_the_source(monkeypatch):
    good = {"district": "District V", "monthlyRent": 1000, "monthlyGroceries": 350, "citySalaryMonthly": 2600,
            "salaryBasis": "NET", "evidencePassages": ["fictional per-person monthly rent USD 1000"]}
    gemini_returns(monkeypatch, good)
    assert extract_local_costs("budapest-v", ai_settings()).status == "EXTRACTED"

    gemini_returns(monkeypatch, {**good, "monthlyRent": 1200})  # hallucinated value
    assert extract_local_costs("budapest-v", ai_settings()).status == "AI_UNAVAILABLE"


def test_address_card_extraction(monkeypatch):
    image = base64.b64encode(SAMPLE_CARD.read_bytes()).decode()
    gemini_returns(monkeypatch, {"documentType": "ADDRESS_CARD", "addressee": "Riley Review", "countryCode": "HU", "city": "Budapest",
                                 "districtName": "District V", "postalCode": "1051", "street": "Minta utca", "building": "12",
                                 "unit": "", "confidence": 0.93, "evidenceQuotes": ["Minta utca 12"]})
    result = extract_address_image(image, "image/png", ai_settings())
    assert result.status == "EXTRACTED" and result.postalCode == "1051" and result.analysisMode == "AI"

    with pytest.raises(ValueError):
        extract_address_image(base64.b64encode(b"\x89PNG other image").decode(), "image/png", ai_settings())


LIVE_KEY = os.environ.get("ANALYSIS_GEMINI_API_KEY", "")


@pytest.mark.skipif(not LIVE_KEY, reason="set ANALYSIS_GEMINI_API_KEY to call the real Gemini API")
def test_live_gemini_social_local_costs_and_address():
    settings = Settings(demo_provider_mode="AI", ai_provider="GEMINI")
    social = analyze_social("repetitive", settings)
    assert social.status == "COMPLETE", "social analysis fell back; check the model name and key"
    assert social.findings and all(f.evidenceIds for f in social.findings)

    assert extract_local_costs("budapest-xi", settings).status == "EXTRACTED"

    card = extract_address_image(base64.b64encode(SAMPLE_CARD.read_bytes()).decode(), "image/png", settings)
    assert card.status == "EXTRACTED" and card.postalCode == "1051"
