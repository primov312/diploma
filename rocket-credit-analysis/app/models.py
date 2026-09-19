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
    aiRequested: bool
    aiStatus: AiStatus
    modelVersion: Optional[str] = None
    aiContributions: Dict[str, float] = {}
