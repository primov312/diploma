"""Opt-in checks against the real TypeSafe API. They run only when ANALYSIS_TYPESAFE_API_KEY is set:

    ANALYSIS_TYPESAFE_API_KEY=... pytest tests/test_jev_live.py
"""
import os

import pytest

from app.analyses import jev
from app.analyses.address import ClaimedAddress, extract_address_image
from app.analyses.housing import ClaimedHousing, extract_housing_document
from app.analyses.local_costs import extract_local_costs
from app.analyses.social import analyze_social
from app.settings import Settings
from tests.jev_support import asset

pytestmark = pytest.mark.skipif(not os.environ.get("ANALYSIS_TYPESAFE_API_KEY"),
                                reason="set ANALYSIS_TYPESAFE_API_KEY to call the real TypeSafe API")
RILEY_ADDRESS = ClaimedAddress(addressee="Riley Review", postalCode="1051", street="Minta utca", building="12")
RILEY_HOME = ClaimedHousing(housingSituation="OWNER", holder="Riley Review", postalCode="1051", street="Minta utca", building="12")


def settings() -> Settings:
    return Settings(demo_provider_mode="AI")


def test_live_social_and_local_costs():
    repetitive = analyze_social("repetitive", settings())
    assert repetitive.status == "COMPLETE", "social analysis fell back; see the 'ai call failed' log line"
    assert {"PROMOTIONAL_CONTENT", "LOW_TOPIC_VARIETY"} & {f.label for f in repetitive.findings}
    assert extract_local_costs("budapest-xi", settings()).status == "EXTRACTED"


def test_live_address_document():
    result = extract_address_image(asset("sample-address-card-riley.png"), "image/png", RILEY_ADDRESS, settings())
    assert result.status == "EXTRACTED" and result.documentType == "ADDRESS_DOCUMENT"
    assert all(p >= jev.CONFIRM for p in result.fieldChecks.values()), result.fieldChecks
    wrong = extract_address_image(asset("sample-address-card-riley.png"), "image/png",
                                  RILEY_ADDRESS.model_copy(update={"postalCode": "1239"}), settings())
    assert wrong.fieldChecks["postalCode"] <= 0.15, wrong.fieldChecks


@pytest.mark.parametrize("name,document_type", [("renting", "RENTAL_AGREEMENT"), ("owner", "OWNERSHIP_RECORD"),
                                                ("family", "FAMILY_DECLARATION"), ("other", "ACCOMMODATION_CONFIRMATION")])
def test_live_housing_documents(name, document_type):
    result = extract_housing_document(asset(f"sample-housing-{name}.png"), "image/png", RILEY_HOME, settings())
    assert result.status == "EXTRACTED" and result.documentType == document_type, result
    assert all(p >= jev.CONFIRM for p in result.fieldChecks.values()), result.fieldChecks


def test_live_document_made_out_to_someone_else():
    """Any document showing the address is read, but one made out to another person fails the name check."""
    result = extract_address_image(asset("sample-housing-renting.png"), "image/png",
                                   RILEY_ADDRESS.model_copy(update={"addressee": "Avery Approve"}), settings())
    assert result.fieldChecks["addressee"] <= 0.15, result.fieldChecks
