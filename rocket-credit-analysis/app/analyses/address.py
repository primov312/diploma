"""Address documents: does the uploaded document show the customer's name and saved address?

Jev checks the name, postal code, street and building number (and the flat, when one is saved). The district
is not asked: the backend derives it from the postal code, so a confirmed postal code validates the district.
"""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from app.analyses.documents import AnalysisMode, DataSource, DocumentCheck, check_document
from app.settings import Settings

AddressDocumentType = Literal["ADDRESS_DOCUMENT", "OTHER", "UNCERTAIN"]


class ClaimedAddress(BaseModel):
    """The customer's name and saved address; each detail is checked against the document."""
    model_config = ConfigDict(extra="forbid")
    addressee: str = Field(..., min_length=1, max_length=200)
    postalCode: str = Field(..., min_length=1, max_length=10)
    street: str = Field(..., min_length=1, max_length=200)
    building: str = Field(..., min_length=1, max_length=40)
    unit: str | None = Field(None, max_length=40)


class AddressImageRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    imageData: str = Field(..., min_length=1, max_length=7_000_000)
    mimeType: Literal["image/png", "image/jpeg"]
    claimed: ClaimedAddress


class AddressExtraction(BaseModel):
    status: Literal["EXTRACTED", "AI_UNAVAILABLE"]
    dataSource: DataSource
    analysisMode: AnalysisMode
    provider: str
    modelVersion: str | None
    promptVersion: str
    documentType: AddressDocumentType | None = None
    addressee: str | None = None
    postalCode: str | None = None
    street: str | None = None
    building: str | None = None
    unit: str | None = None
    confidence: float | None = None
    evidenceQuotes: list[str] = Field(default_factory=list)
    # Probability that the document shows each claimed detail. The backend applies the policy.
    fieldChecks: dict[str, float] | None = None


ADDRESS_CHECK = DocumentCheck(
    name="address",
    kind_question="What kind of document is the image in `document`?",
    kinds={"ADDRESS_DOCUMENT": "A document that shows a person's name and home postal address, such as an address card, "
                               "utility bill, bank statement, official letter, or a tenancy or ownership document.",
           "OTHER": "Some other document or picture."},
    fields={
        "addressee": "Is the document in `document` addressed to or issued for the person `claimed.addressee`?",
        "postalCode": "Does the address on the document in `document` have the postal code `claimed.postalCode`?",
        "street": "Is the address on the document in `document` on the street `claimed.street`?",
        "building": "Is the building number of the address on the document in `document` `claimed.building`?",
        "unit": "Does the address on the document in `document` include the flat, floor or door `claimed.unit`?",
    },
    # demo-repository/src/assets/sample-address-card-riley.png
    samples={"1f4e3b44df56c1d17fee529e0f0592c1fd234537acccee5ca05c4d7b7e3918ed": ("ADDRESS_DOCUMENT", {
        "addressee": "Riley Review", "postalCode": "1051", "street": "Minta utca", "building": "12"})},
)


def extract_address_image(image_data: str, mime_type: str, claimed: ClaimedAddress, settings: Settings) -> AddressExtraction:
    return AddressExtraction(**check_document(ADDRESS_CHECK, image_data, mime_type, claimed.model_dump(), settings))
