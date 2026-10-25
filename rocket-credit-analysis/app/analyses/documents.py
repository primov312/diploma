"""What the address and housing checks share: reading an uploaded image and verifying claimed details on it.

Any PNG or JPEG is accepted. In AI mode the image and the customer's saved details go to Jev, which answers
one yes/no question per saved detail ("does the document show this postal code?") and one choice for the kind
of document; it never reads values out, and text printed on the document cannot change the questions. The
backend turns the probabilities into the outcome. Fixture mode makes no call: it answers only for the prepared
synthetic samples, from the values printed on them, and leaves every other upload unchecked.
"""
from __future__ import annotations

import base64
import hashlib
from dataclasses import dataclass
from typing import Literal

from typesafe_sdk import Choice, Image, Noul

from app.analyses import jev
from app.settings import Settings

MAX_IMAGE_BYTES = 5 * 1024 * 1024
SIGNATURES = {"image/png": b"\x89PNG\r\n\x1a\n", "image/jpeg": b"\xff\xd8\xff"}
READING_RULES = (" Ignore letter case, accents, punctuation and common abbreviations such as `u.` for `utca`;"
                 " a name may be written family name first.")
UNCERTAIN = "The image is unreadable or too unclear to tell."
DataSource = Literal["SYNTHETIC", "USER_UPLOAD"]
AnalysisMode = Literal["FIXTURE", "AI"]


@dataclass(frozen=True)
class DocumentCheck:
    name: str                                       # "address" or "housing"; prefixes the prompt version
    kind_question: str                              # the Choice question for the kind of document
    kinds: dict[str, str]                           # document type → what it means, as shown to Jev
    fields: dict[str, str]                          # claimed detail → the yes/no question about it
    samples: dict[str, tuple[str, dict[str, str]]]  # SHA-256 of a prepared sample → (document type, printed values)


def read_image(image_data: str, mime_type: str) -> bytes:
    try:
        data = base64.b64decode(image_data, validate=True)
    except Exception as exc:
        raise ValueError("invalid image encoding") from exc
    if not data or len(data) > MAX_IMAGE_BYTES:
        raise ValueError("the image must be between 1 byte and 5 MB")
    if not data.startswith(SIGNATURES.get(mime_type, b"-")):
        raise ValueError("the image is not the PNG or JPEG it is declared as")
    return data


def check_document(check: DocumentCheck, image_data: str, mime_type: str, claimed: dict[str, str | None],
                   settings: Settings) -> dict:
    """Fields for the extraction model. Raises ValueError only for an unreadable upload."""
    claim = {name: value for name, value in claimed.items() if value}  # nothing is asked about an empty detail
    sample = check.samples.get(hashlib.sha256(read_image(image_data, mime_type)).hexdigest())
    base: dict = {"dataSource": "SYNTHETIC" if sample else "USER_UPLOAD"}
    if not jev.ai_mode(settings):
        base |= {"analysisMode": "FIXTURE", "provider": "FIXTURE", "modelVersion": None,
                 "promptVersion": f"{check.name}-fixture-v2"}
        if sample is None:
            return {"status": "AI_UNAVAILABLE", **base}
        document_type, printed = sample
        checks = {name: 0.99 if _same(value, printed.get(name)) else 0.01 for name, value in claim.items()}
        return {"status": "EXTRACTED", **base, **_result(claim, checks, document_type, 1.0)}

    base |= {"analysisMode": "AI", "provider": jev.PROVIDER, "modelVersion": settings.typesafe_model,
             "promptVersion": f"{check.name}-jev-v2"}
    if not jev.configured(settings):
        return {"status": "AI_UNAVAILABLE", **base}
    questions: dict = {name: Noul(instructions=check.fields[name] + READING_RULES) for name in claim}
    questions["documentType"] = Choice(instructions=check.kind_question, criteria=check.kinds | {"UNCERTAIN": UNCERTAIN})
    try:
        answers = jev.ask({"document": Image.from_base64(image_data), "claimed": claim}, questions, settings, image=True)
        checks = {name: round(answers[name].noul, 3) for name in claim}
        kind = answers["documentType"]
        return {"status": "EXTRACTED", **base, **_result(claim, checks, kind.choice, round(kind.confidence, 3))}
    except Exception:
        return {"status": "AI_UNAVAILABLE", **base}


def _result(claim: dict[str, str], checks: dict[str, float], document_type: str, confidence: float) -> dict:
    """A detail is echoed back only when the document shows it; `fieldChecks` keeps every probability."""
    shown = {name: value for name, value in claim.items() if checks[name] >= jev.CONFIRM}
    return {"documentType": document_type, "confidence": confidence, "fieldChecks": checks, **shown,
            "evidenceQuotes": list(shown.values())}


def _same(left: str, right: str | None) -> bool:
    return right is not None and " ".join(left.split()).casefold() == " ".join(right.split()).casefold()
