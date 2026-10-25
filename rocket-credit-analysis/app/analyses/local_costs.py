"""Local living costs from a repository-owned synthetic source sheet.

Select instead of generate: code finds every amount and district in the source, Jev picks which one plays
each role, and code copies the picked string and checks it against the source before anything is returned.
"""
from __future__ import annotations

import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from typesafe_sdk import Choice

from app.analyses import jev
from app.settings import Settings


class LocalCostRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    districtId: str = Field(..., pattern=r"^budapest-(v|xi|xiii)$")


class LocalCostExtraction(BaseModel):
    status: Literal["EXTRACTED", "AI_UNAVAILABLE"]
    districtId: str
    datasetVersion: str
    dataSource: Literal["SYNTHETIC"] = "SYNTHETIC"
    analysisMode: Literal["FIXTURE", "AI"]
    provider: str
    modelVersion: str | None
    promptVersion: str
    monthlyRent: float | None = None
    monthlyGroceries: float | None = None
    citySalaryMonthly: float | None = None
    salaryBasis: Literal["GROSS", "NET"] | None = None
    currency: Literal["USD"] = "USD"
    evidencePassages: list[str] = Field(default_factory=list)


DATASET_VERSION = "budapest-demo-v1"
LOCAL_COST_DOCUMENTS = {
    "budapest-v": ("District V", 1000, 350, 2600),
    "budapest-xi": ("District XI", 820, 320, 2600),
    "budapest-xiii": ("District XIII", 760, 310, 2600),
}
USD_AMOUNT = re.compile(r"\bUSD \d+\b")
DISTRICT_NAME = re.compile(r"\bDistrict [IVXL]+\b")
ROLES = {
    "monthlyRent": "Which amount in `source` is the monthly rent per person?",
    "monthlyGroceries": "Which amount in `source` is the monthly grocery spending per person?",
    "citySalaryMonthly": "Which amount in `source` is the monthly city salary?",
}


def extract_local_costs(district_id: str, settings: Settings) -> LocalCostExtraction:
    if district_id not in LOCAL_COST_DOCUMENTS:
        raise ValueError("unknown synthetic district")
    district, rent, groceries, salary = LOCAL_COST_DOCUMENTS[district_id]
    source = (
        f"Synthetic source sheet {DATASET_VERSION}. {district}: fictional per-person monthly rent USD {rent}; "
        f"fictional per-person monthly groceries USD {groceries}; fictional city net salary context USD {salary}. "
        "All figures are invented for a diploma demonstration."
    )
    base = {"districtId": district_id, "datasetVersion": DATASET_VERSION}
    values = {"monthlyRent": rent, "monthlyGroceries": groceries, "citySalaryMonthly": salary, "salaryBasis": "NET"}
    if not jev.ai_mode(settings):
        return LocalCostExtraction(status="EXTRACTED", **base, analysisMode="FIXTURE", provider="FIXTURE", modelVersion=None,
                                   promptVersion="local-costs-fixture-v1", **values, evidencePassages=[source])
    ai = {**base, "analysisMode": "AI", "provider": jev.PROVIDER, "modelVersion": settings.typesafe_model,
          "promptVersion": "local-costs-jev-v1"}
    if not jev.configured(settings):
        return LocalCostExtraction(status="AI_UNAVAILABLE", **ai)
    try:
        parsed = _pick_values(source, settings)
        valid = parsed["district"].casefold() == district.casefold() and parsed["monthlyRent"] == rent \
            and parsed["monthlyGroceries"] == groceries and parsed["citySalaryMonthly"] == salary \
            and parsed["salaryBasis"] == "NET" and parsed["evidencePassages"]
        if not valid or any(passage not in source for passage in parsed["evidencePassages"]):
            jev.log.warning("ai output rejected", extra={"model": settings.typesafe_model, "check": "local-cost-source"})
            raise ValueError("model output failed source validation")
        return LocalCostExtraction(status="EXTRACTED", **ai, **values, evidencePassages=parsed["evidencePassages"])
    except Exception:
        return LocalCostExtraction(status="AI_UNAVAILABLE", **ai)


def _pick_values(source: str, settings: Settings) -> dict:
    amounts = list(dict.fromkeys(USD_AMOUNT.findall(source)))
    districts = list(dict.fromkeys(DISTRICT_NAME.findall(source)))

    def pick(question: str, candidates: list[str], missing: str) -> Choice:
        return Choice(instructions=question, criteria={c: None for c in candidates} | {jev.NONE: missing})

    questions = {name: pick(text, amounts, "No amount in `source` plays this role.") for name, text in ROLES.items()}
    questions["district"] = pick("Which district do the figures in `source` describe?", districts, "`source` names no district.")
    questions["salaryBasis"] = Choice(instructions="Is the salary in `source` stated before or after tax?",
                                      criteria={"GROSS": "Before tax.", "NET": "After tax (net).", jev.NONE: "`source` does not say."})
    answers = jev.ask({"source": source}, questions, settings)
    if any(answer.choice == jev.NONE or answer.confidence < jev.MIN_CHOICE for answer in answers.values()):
        jev.log.warning("ai output rejected", extra={"model": settings.typesafe_model, "check": "local-cost-selection"})
        raise ValueError("no confident selection")
    clauses = re.split(r"(?<=[;.]) ", source)
    picked = {name: answers[name].choice for name in ROLES}
    return {"district": answers["district"].choice, "salaryBasis": answers["salaryBasis"].choice,
            **{name: int(value.removeprefix("USD ")) for name, value in picked.items()},
            "evidencePassages": [next(c for c in clauses if re.search(re.escape(value) + r"\b", c)) for value in picked.values()]}
