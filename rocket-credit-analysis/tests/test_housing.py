"""Housing documents: any PNG or JPEG is checked for the name, the saved address and how the home is held."""
import base64

import pytest
from fastapi.testclient import TestClient

from app.analyses.housing import ClaimedHousing, extract_housing_document
from app.main import app
from app.settings import Settings
from tests.jev_support import asset, document_answers, jev_answers, jev_settings

SAMPLES = {"renting": "RENTAL_AGREEMENT", "owner": "OWNERSHIP_RECORD", "family": "FAMILY_DECLARATION",
           "other": "ACCOMMODATION_CONFIRMATION"}
RILEY = ClaimedHousing(housingSituation="OWNER", holder="Riley Review", postalCode="1051", street="Minta utca", building="12")
CONTRACT = base64.b64encode(b"\x89PNG\r\n\x1a\n a customer's scan of a purchase contract").decode()


def sample(name: str) -> str:
    return asset(f"sample-housing-{name}.png")


@pytest.mark.parametrize("name,document_type", SAMPLES.items())
def test_fixture_mode_recognizes_each_prepared_document(name, document_type):
    result = extract_housing_document(sample(name), "image/png", RILEY, Settings())
    assert result.status == "EXTRACTED" and result.analysisMode == "FIXTURE" and result.documentType == document_type
    assert (result.holder, result.postalCode, result.street, result.building) == ("Riley Review", "1051", "Minta utca", "12")
    assert result.dataSource == "SYNTHETIC" and set(result.fieldChecks.values()) == {0.99}


def test_fixture_mode_reports_a_different_name_and_leaves_other_uploads_unchecked():
    avery = extract_housing_document(sample("owner"), "image/png", RILEY.model_copy(update={"holder": "Avery Approve"}), Settings())
    assert avery.fieldChecks["holder"] == 0.01 and avery.holder is None
    other = extract_housing_document(CONTRACT, "image/png", RILEY, Settings())
    assert other.status == "AI_UNAVAILABLE" and other.dataSource == "USER_UPLOAD"


def test_any_upload_is_checked_without_revealing_the_requested_situation(monkeypatch):
    calls: list = []
    jev_answers(monkeypatch, document_answers("OWNERSHIP_RECORD", {"building": 0.08}, default=0.96), calls)

    result = extract_housing_document(CONTRACT, "image/png", RILEY, jev_settings())

    assert result.status == "EXTRACTED" and result.provider == "TYPESAFE" and result.promptVersion == "housing-jev-v2"
    assert result.dataSource == "USER_UPLOAD" and result.documentType == "OWNERSHIP_RECORD" and result.holder == "Riley Review"
    assert result.building is None and result.fieldChecks["building"] == 0.08  # not confirmed: not echoed back
    body = calls[0]["body"]
    assert set(body["questions"]) == {"holder", "postalCode", "street", "building", "documentType"}
    assert "housingSituation" not in str(body)
    assert "purchase contract" in body["questions"]["documentType"]["criteria"]["OWNERSHIP_RECORD"]
    assert "rents, owns, is buying or lives in the home" in body["questions"]["holder"]["instructions"]
    assert calls[0]["headers"]["typesafe-danger-zone-images"] == "true"


def test_failure_and_missing_key_report_ai_unavailable(monkeypatch):
    jev_answers(monkeypatch, None, status=503)
    failed = extract_housing_document(sample("renting"), "image/png", RILEY, jev_settings())
    assert failed.status == "AI_UNAVAILABLE" and failed.fieldChecks is None
    missing = extract_housing_document(sample("renting"), "image/png", RILEY, jev_settings(typesafe_api_key=""))
    assert missing.status == "AI_UNAVAILABLE" and missing.promptVersion == "housing-jev-v2"


def test_endpoint_requires_token_and_refuses_unreadable_images(monkeypatch):
    monkeypatch.setenv("ANALYSIS_DEMO_PROVIDER_MODE", "FIXTURE")
    body = {"imageData": sample("family"), "mimeType": "image/png", "claimed": RILEY.model_dump()}
    headers = {"X-Analysis-Token": "test-secret"}
    with TestClient(app) as c:
        assert c.post("/demo-analysis/housing", json=body).status_code == 401
        ok = c.post("/demo-analysis/housing", json=body, headers=headers)
        assert ok.status_code == 200 and ok.json()["documentType"] == "FAMILY_DECLARATION"
        other = c.post("/demo-analysis/housing", json={**body, "imageData": CONTRACT}, headers=headers)
        assert other.status_code == 200 and other.json()["status"] == "AI_UNAVAILABLE"
        bad = c.post("/demo-analysis/housing", json={**body, "imageData": base64.b64encode(b"plain text").decode()}, headers=headers)
        assert bad.status_code == 400
