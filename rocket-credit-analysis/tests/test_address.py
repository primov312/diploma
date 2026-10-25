"""Address documents: any PNG or JPEG is checked for the customer's name, postal code, street and building."""
import base64

import pytest
from fastapi.testclient import TestClient

from app.analyses.address import ClaimedAddress, extract_address_image
from app.main import app
from app.settings import Settings
from tests.jev_support import asset, document_answers, jev_answers, jev_settings

RILEY = ClaimedAddress(addressee="Riley Review", postalCode="1051", street="Minta utca", building="12")
PHOTO = base64.b64encode(b"\xff\xd8\xff\xe0 a customer's own photo of a utility bill").decode()


def card() -> str:
    return asset("sample-address-card-riley.png")


def test_fixture_mode_checks_the_sample_card_against_the_saved_address():
    result = extract_address_image(card(), "image/png", RILEY, Settings())
    assert result.status == "EXTRACTED" and result.analysisMode == "FIXTURE" and result.dataSource == "SYNTHETIC"
    assert result.documentType == "ADDRESS_DOCUMENT" and set(result.fieldChecks.values()) == {0.99}
    other = extract_address_image(card(), "image/png", RILEY.model_copy(update={"postalCode": "1239", "unit": "4"}), Settings())
    assert other.fieldChecks == {"addressee": 0.99, "postalCode": 0.01, "street": 0.99, "building": 0.99, "unit": 0.01}
    assert other.postalCode is None and other.street == "Minta utca"


def test_fixture_mode_leaves_any_other_upload_unchecked():
    result = extract_address_image(PHOTO, "image/jpeg", RILEY, Settings())
    assert result.status == "AI_UNAVAILABLE" and result.dataSource == "USER_UPLOAD" and result.fieldChecks is None


def test_any_upload_is_checked_by_jev_for_each_saved_detail(monkeypatch):
    calls: list = []
    jev_answers(monkeypatch, document_answers("ADDRESS_DOCUMENT", {"postalCode": 0.1}), calls)

    result = extract_address_image(PHOTO, "image/jpeg", RILEY, jev_settings())

    assert result.status == "EXTRACTED" and result.dataSource == "USER_UPLOAD" and result.provider == "TYPESAFE"
    assert result.promptVersion == "address-jev-v2" and result.documentType == "ADDRESS_DOCUMENT"
    assert result.fieldChecks == {"addressee": 0.97, "postalCode": 0.1, "street": 0.97, "building": 0.97}
    assert result.postalCode is None and result.street == "Minta utca"  # only confirmed details are echoed back
    request = calls[0]
    assert request["headers"]["typesafe-danger-zone-images"] == "true"
    body = request["body"]
    assert set(body["questions"]) == {"addressee", "postalCode", "street", "building", "documentType"}  # no unit saved
    assert body["state"]["claimed"] == RILEY.model_dump(exclude_none=True)
    assert "family name first" in body["questions"]["addressee"]["instructions"]
    assert body["assets"][body["state"]["document"]]["type"] == "image" and base64.b64decode(PHOTO) in request["raw"]


def test_a_saved_flat_is_checked_too(monkeypatch):
    calls: list = []
    jev_answers(monkeypatch, document_answers("ADDRESS_DOCUMENT"), calls)
    result = extract_address_image(card(), "image/png", RILEY.model_copy(update={"unit": "3/5"}), jev_settings())
    assert result.unit == "3/5" and result.dataSource == "SYNTHETIC" and "unit" in calls[0]["body"]["questions"]


def test_failure_and_missing_key_report_ai_unavailable(monkeypatch):
    jev_answers(monkeypatch, None, status=503)
    assert extract_address_image(PHOTO, "image/jpeg", RILEY, jev_settings()).status == "AI_UNAVAILABLE"
    missing = extract_address_image(PHOTO, "image/jpeg", RILEY, jev_settings(typesafe_api_key=""))
    assert missing.status == "AI_UNAVAILABLE" and missing.analysisMode == "AI"


@pytest.mark.parametrize("data,mime", [("not base64!", "image/png"), (PHOTO, "image/png"),
                                        (base64.b64encode(b"GIF89a").decode(), "image/jpeg")])
def test_unreadable_or_mislabelled_images_are_refused(data, mime):
    with pytest.raises(ValueError):
        extract_address_image(data, mime, RILEY, jev_settings())


def test_endpoint_requires_token_and_a_claim(monkeypatch):
    monkeypatch.setenv("ANALYSIS_DEMO_PROVIDER_MODE", "FIXTURE")
    body = {"imageData": card(), "mimeType": "image/png", "claimed": RILEY.model_dump()}
    headers = {"X-Analysis-Token": "test-secret"}
    with TestClient(app) as c:
        assert c.post("/demo-analysis/address", json=body).status_code == 401
        assert c.post("/demo-analysis/address", json=body, headers=headers).json()["documentType"] == "ADDRESS_DOCUMENT"
        assert c.post("/demo-analysis/address", json={**body, "claimed": None}, headers=headers).status_code == 422
        bad = c.post("/demo-analysis/address", json={**body, "mimeType": "image/jpeg"}, headers=headers)
        assert bad.status_code == 400 and "not the PNG or JPEG" in bad.json()["detail"]
