"""Housing documents: does the uploaded document show how the customer lives at their saved address?

Jev checks the name, postal code, street and building number, and chooses what the document shows about the
home: rented, owned or being bought, shared rent-free with family, or provided accommodation. The requested
situation is not sent, so it cannot steer that choice; the backend compares the two.
"""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from app.analyses.documents import AnalysisMode, DataSource, DocumentCheck, check_document
from app.settings import Settings

HousingDocumentType = Literal["RENTAL_AGREEMENT", "OWNERSHIP_RECORD", "FAMILY_DECLARATION",
                              "ACCOMMODATION_CONFIRMATION", "OTHER", "UNCERTAIN"]


class ClaimedHousing(BaseModel):
    """The housing situation the customer asks for, at their saved address."""
    model_config = ConfigDict(extra="forbid")
    housingSituation: Literal["RENTING", "OWNER", "FAMILY", "OTHER"]
    holder: str = Field(..., min_length=1, max_length=200)
    postalCode: str = Field(..., min_length=1, max_length=10)
    street: str = Field(..., min_length=1, max_length=200)
    building: str = Field(..., min_length=1, max_length=40)


class HousingDocumentRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    imageData: str = Field(..., min_length=1, max_length=7_000_000)
    mimeType: Literal["image/png", "image/jpeg"]
    claimed: ClaimedHousing


class HousingDocumentExtraction(BaseModel):
    status: Literal["EXTRACTED", "AI_UNAVAILABLE"]
    dataSource: DataSource
    analysisMode: AnalysisMode
    provider: str
    modelVersion: str | None
    promptVersion: str
    documentType: HousingDocumentType | None = None
    holder: str | None = None
    postalCode: str | None = None
    street: str | None = None
    building: str | None = None
    confidence: float | None = None
    evidenceQuotes: list[str] = Field(default_factory=list)
    # Probability that the document shows each claimed detail. The backend applies the policy.
    fieldChecks: dict[str, float] | None = None


RILEY_HOME = {"holder": "Riley Review", "postalCode": "1051", "street": "Minta utca", "building": "12"}

HOUSING_CHECK = DocumentCheck(
    name="housing",
    kind_question="What does the document in `document` show about how `claimed.holder` lives in the home?",
    kinds={
        "RENTAL_AGREEMENT": "They rent it: a lease or rental agreement, or another document naming them as the tenant.",
        "OWNERSHIP_RECORD": "They own it or are buying it: a land registry extract, ownership certificate or property "
                            "purchase contract naming them as owner or buyer.",
        "FAMILY_DECLARATION": "They live there rent-free with a family member: a declaration by the relative who owns or rents it.",
        "ACCOMMODATION_CONFIRMATION": "They live in provided accommodation, such as a dormitory, serviced room or "
                                      "employer housing, confirmed by the provider.",
        "OTHER": "The document does not show how they live there, or it is some other document or picture.",
    },
    fields={
        "holder": "Does the document in `document` name `claimed.holder` as the person who rents, owns, is buying or lives in the home?",
        "postalCode": "Does the document in `document` show the postal code `claimed.postalCode` for the home?",
        "street": "Does the document in `document` show the street `claimed.street` for the home?",
        "building": "Does the document in `document` show the building number `claimed.building` for the home?",
    },
    # demo-repository/src/assets/sample-housing-*.png, all for Riley Review at Minta utca 12, 1051 Budapest.
    samples={
        "4cde71469d9c692b8978efa44aa01bb434f1bab41981e445b88d6ee5b3d401d1": ("RENTAL_AGREEMENT", RILEY_HOME),
        "982edcc525ce25102045cc49e7c7957e8d7387840464134a8e48f147b4cee331": ("OWNERSHIP_RECORD", RILEY_HOME),
        "4d238ba3891a4a8fe5267ed2fb42a99be8ffa6fd183a9ad7512819ba007a89b5": ("FAMILY_DECLARATION", RILEY_HOME),
        "59f711bd81f566f12f2b170c0e0324f3bb2eb27534e1cb4e5302ff52c09e9c70": ("ACCOMMODATION_CONFIRMATION", RILEY_HOME),
    },
)


def extract_housing_document(image_data: str, mime_type: str, claimed: ClaimedHousing,
                             settings: Settings) -> HousingDocumentExtraction:
    details = claimed.model_dump(exclude={"housingSituation"})
    return HousingDocumentExtraction(**check_document(HOUSING_CHECK, image_data, mime_type, details, settings))
