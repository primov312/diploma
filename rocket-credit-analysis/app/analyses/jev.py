"""The one AI client: Jev (TypeSafe System One).

Jev answers typed questions only — yes/no probabilities (noul), choices among supplied options and ordered
scores — so each analysis finds the candidates in code, asks narrow questions and writes every value and
report sentence itself. Nothing Jev reads (post text, document images) can change what a report says.
"""
from __future__ import annotations

import logging
from typing import Literal

import typesafe_sdk

from app.settings import Settings

log = logging.getLogger("rocket.analysis.ai")
PROVIDER = "TYPESAFE"

# Demo thresholds, chosen by hand for the synthetic scenarios — not calibrated on real data.
FLAG = 0.5        # a post check counts as "yes" from here
CONFIRM = 0.85    # a claimed field counts as shown on a document from here (the backend uses the same value)
MIN_CHOICE = 0.6  # minimum Choice confidence for a copied value
NONE = "none"


def ai_mode(settings: Settings) -> bool:
    return settings.demo_provider_mode.upper() == "AI"


def configured(settings: Settings) -> bool:
    return bool(settings.typesafe_api_key)


def ask(state: dict, questions: dict, settings: Settings, image: bool = False) -> dict:
    """One System One request; answers keyed by question name. Raises on any problem; callers fall back.

    Image input is a TypeSafe preview and must be opted into per request. Only the model and the failure
    type or HTTP status are logged — never the state, the questions or the answers.
    """
    base = {"provider": PROVIDER, "model": settings.typesafe_model}
    try:
        with typesafe_sdk.TypeSafeClient(api_key=settings.typesafe_api_key, model=settings.typesafe_model,
                                         timeout=float(settings.typesafe_timeout)) as client:
            return client.system_one(state=state, questions=questions,
                                     extra_headers={"TypeSafe-Danger-Zone-Images": "true"} if image else None).answers
    except typesafe_sdk.TypeSafeAPIError as exc:
        log.warning("ai call failed", extra={**base, "httpStatus": exc.status})
        raise
    except Exception as exc:
        log.warning("ai call failed", extra={**base, "error": type(exc).__name__})
        raise


def band(probability: float) -> Literal["LOW", "MEDIUM", "HIGH"]:
    return "HIGH" if probability >= 0.9 else "MEDIUM" if probability >= 0.7 else "LOW"
