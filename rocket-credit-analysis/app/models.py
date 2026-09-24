# app/models.py
"""Request/response contract between the Java backend and this service.

The bundle contains derived features only: no names, emails, user IDs,
password hashes or session data. Each feature section is optional so the
backend can report that evidence is missing; the combiner turns missing
required evidence into REVIEW rather than guessing.
"""
from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Dict, List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field


class ProfileFeatures(BaseModel):
    model_config = ConfigDict(extra="forbid")

    accountAgeMonths: int = Field(..., ge=0)
    profileComplete: bool
    emailVerified: bool


class HistoryFeatures(BaseModel):
    """Purchase history with the partner the customer is applying at, plus a
    small cross-partner summary."""

    model_config = ConfigDict(extra="forbid")

    partnerOrders12m: int = Field(..., ge=0)
    partnerAvgOrderValue: Decimal = Field(..., ge=0)
    partnerRefundRate: float = Field(..., ge=0.0, le=1.0)
    partnerOnTimeRatio: float = Field(..., ge=0.0, le=1.0)
    partnerTenureMonths: int = Field(..., ge=0)
    totalOrders12m: int = Field(..., ge=0)


class FinanceFeatures(BaseModel):
    model_config = ConfigDict(extra="forbid")

    monthlyIncome: Decimal = Field(..., ge=0)
    monthlyExpenses: Decimal = Field(..., ge=0)
    monthlyObligations: Decimal = Field(..., ge=0)


class AffordabilityInputs(BaseModel):
    """Authoritative v2 calculation inputs. Category mode is all-or-nothing."""

    model_config = ConfigDict(extra="forbid")

    monthlyNetIncome: Optional[Decimal] = Field(None, ge=0, max_digits=12, decimal_places=2)
    housingSituation: Literal["RENTING", "OWNER", "FAMILY", "OTHER"]
    expenseMode: Literal["ITEMIZED", "AGGREGATE"]
    housingCost: Optional[Decimal] = Field(None, ge=0, max_digits=12, decimal_places=2)
    groceriesCost: Optional[Decimal] = Field(None, ge=0, max_digits=12, decimal_places=2)
    utilitiesCost: Optional[Decimal] = Field(None, ge=0, max_digits=12, decimal_places=2)
    transportCost: Optional[Decimal] = Field(None, ge=0, max_digits=12, decimal_places=2)
    otherLivingCosts: Optional[Decimal] = Field(None, ge=0, max_digits=12, decimal_places=2)
    legacyLivingExpenses: Optional[Decimal] = Field(None, ge=0, max_digits=12, decimal_places=2)
    monthlyObligations: Decimal = Field(..., ge=0, max_digits=12, decimal_places=2)
    districtRentReference: Optional[Decimal] = Field(None, ge=0, max_digits=12, decimal_places=2)
    districtGroceryReference: Optional[Decimal] = Field(None, ge=0, max_digits=12, decimal_places=2)
    referencesEligible: bool = False
    partnerCap: Decimal = Field(..., ge=0, max_digits=12, decimal_places=2)
    financialRevision: Optional[int] = Field(None, ge=1)
    generation: Optional[int] = Field(None, ge=1)


class AffordabilityRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    inputs: AffordabilityInputs


class AffordabilityResponse(BaseModel):
    formulaVersion: str
    policyVersion: str
    currency: Literal["USD"] = "USD"
    termMonths: int
    baseAmount: Optional[Decimal]
    partnerAmount: Optional[Decimal]
    monthlyPaymentCapacity: Optional[Decimal]
    breakdown: Dict[str, Optional[Decimal]]
    reasons: List[str]


class ScoreRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    requestedAmount: Decimal = Field(..., gt=0)
    currency: Literal["USD"] = "USD"
    partnerCap: Decimal = Field(..., gt=0)
    useAi: bool = False
    observedAt: datetime
    profile: Optional[ProfileFeatures] = None
    history: Optional[HistoryFeatures] = None
    finance: Optional[FinanceFeatures] = None
    # New callers can opt into the shared v2 contract. Existing callers remain
    # on the legacy finance fields until Java has migrated its feature bundle.
    affordability: Optional[AffordabilityInputs] = None


class DecisionStatus(str, Enum):
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"
    REVIEW = "REVIEW"


class AiStatus(str, Enum):
    NOT_REQUESTED = "NOT_REQUESTED"
    UNAVAILABLE = "UNAVAILABLE"  # requested, model missing/failed -> rules only
    APPLIED = "APPLIED"


class FactorResult(BaseModel):
    score: Optional[float] = None
    weight: float
    reasons: List[str] = []
    details: Dict[str, float | int | bool | str | None] = {}


class ScoreResponse(BaseModel):
    decisionStatus: DecisionStatus
    score: float = Field(..., ge=0.0, le=1.0)
    possibleAmount: Decimal = Field(..., ge=0)
    currency: Literal["USD"] = "USD"
    reasons: List[str]
    factors: Dict[str, FactorResult]
    policyVersion: str
    formulaVersion: Optional[str] = None
    aiRequested: bool
    aiStatus: AiStatus
    modelVersion: Optional[str] = None
    aiContributions: Dict[str, float] = {}
