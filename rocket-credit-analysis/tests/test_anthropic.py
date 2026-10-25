"""Anthropic (Claude Haiku 5.5) as the AI provider, with the SDK client faked, plus an opt-in live check.

The live test runs only when ANALYSIS_ANTHROPIC_API_KEY is set:

    ANALYSIS_ANTHROPIC_API_KEY=... pytest tests/test_anthropic.py -k live
"""
import base64
import json
import os
from pathlib import Path
from types import SimpleNamespace

import pytest

from app import demo_analysis
from app.demo_analysis import analyze_social, extract_address_image, extract_local_costs
from app.settings import Settings

SAMPLE_CARD = Path(__file__).resolve().parents[2] / "demo-repository/src/assets/sample-address-card-riley.png"


def claude_settings(**overrides) -> Settings:
    values = {"demo_provider_mode": "AI", "ai_provider": "ANTHROPIC", "anthropic_api_key": "test-key", "gemini_api_key": ""}
    return Settings(**{**values, **overrides})


def claude_returns(monkeypatch, payload: dict | None, calls: list | None = None, stop_reason: str = "end_turn"):
    class FakeMessages:
        def create(self, **kwargs):
            if calls is not None:
                calls.append(kwargs)
            content = [] if payload is None else [SimpleNamespace(type="text", text=json.dumps(payload))]
            return SimpleNamespace(stop_reason=stop_reason, content=content)

    class FakeClient:
        def __init__(self, **kwargs):
            if calls is not None:
                calls.append({"client": kwargs})
            self.messages = FakeMessages()

    monkeypatch.setattr(demo_analysis.anthropic, "Anthropic", FakeClient)


def test_social_report_uses_haiku_with_structured_output(monkeypatch):
    calls: list = []
    claude_returns(monkeypatch, {"summary": "Repeated promotional text.", "findings": [
        {"label": "REPETITION", "observation": "Same text in six posts.", "evidenceIds": ["post-02", "post-03"], "confidence": "MEDIUM"}]}, calls)

    report = analyze_social("repetitive", claude_settings())

    assert report.status == "COMPLETE" and report.provider == "ANTHROPIC" and report.modelVersion == "claude-haiku-5-5"
    client, request = calls
    assert client["client"]["api_key"] == "test-key"
    assert request["model"] == "claude-haiku-5-5"
    assert request["output_config"]["effort"] == "low"
    schema = request["output_config"]["format"]["schema"]
    assert request["output_config"]["format"]["type"] == "json_schema"
    assert schema["type"] == "object" and schema["additionalProperties"] is False
    assert schema["properties"]["findings"]["items"]["properties"]["confidence"]["enum"] == ["LOW", "MEDIUM", "HIGH", "INCONCLUSIVE"]
    assert "temperature" not in request  # Haiku 5.5 rejects non-default sampling parameters


def test_address_card_is_sent_as_an_image_block(monkeypatch):
    calls: list = []
    claude_returns(monkeypatch, {"documentType": "ADDRESS_CARD", "addressee": "Riley Review", "countryCode": "HU", "city": "Budapest",
                                 "districtName": "District V", "postalCode": "1051", "street": "Minta utca", "building": "12",
                                 "unit": "", "confidence": 0.95, "evidenceQuotes": ["Minta utca 12"]}, calls)
    image = base64.b64encode(SAMPLE_CARD.read_bytes()).decode()

    result = extract_address_image(image, "image/png", claude_settings())

    assert result.status == "EXTRACTED" and result.provider == "ANTHROPIC" and result.postalCode == "1051"
    content = calls[1]["messages"][0]["content"]
    assert content[0] == {"type": "image", "source": {"type": "base64", "media_type": "image/png", "data": image}}
    assert content[1]["type"] == "text"


def test_refusal_and_missing_key_fall_back(monkeypatch):
    claude_returns(monkeypatch, None, stop_reason="refusal")
    assert analyze_social("ordinary", claude_settings()).status == "AI_UNAVAILABLE"
    assert extract_local_costs("budapest-v", claude_settings()).status == "AI_UNAVAILABLE"
    no_key = analyze_social("ordinary", claude_settings(anthropic_api_key=""))
    assert no_key.status == "AI_UNAVAILABLE" and no_key.provider == "ANTHROPIC"


def test_hallucinated_figures_are_still_rejected(monkeypatch):
    claude_returns(monkeypatch, {"district": "District V", "monthlyRent": 999, "monthlyGroceries": 350, "citySalaryMonthly": 2600,
                                 "salaryBasis": "NET", "evidencePassages": ["fictional per-person monthly rent USD 1000"]})
    assert extract_local_costs("budapest-v", claude_settings()).status == "AI_UNAVAILABLE"


LIVE_KEY = os.environ.get("ANALYSIS_ANTHROPIC_API_KEY", "")


@pytest.mark.skipif(not LIVE_KEY, reason="set ANALYSIS_ANTHROPIC_API_KEY to call the real Anthropic API")
def test_live_claude_social_local_costs_and_address():
    settings = Settings(demo_provider_mode="AI", ai_provider="ANTHROPIC")
    social = analyze_social("repetitive", settings)
    assert social.status == "COMPLETE", "social analysis fell back; see the 'ai call failed' log line"
    assert social.findings and all(f.evidenceIds for f in social.findings)

    assert extract_local_costs("budapest-xi", settings).status == "EXTRACTED"

    card = extract_address_image(base64.b64encode(SAMPLE_CARD.read_bytes()).decode(), "image/png", settings)
    assert card.status == "EXTRACTED" and card.postalCode == "1051"
