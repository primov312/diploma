from __future__ import annotations
from typing import Optional, Any, Dict
from pydantic import BaseModel, Field, root_validator  

class ClaimRef(BaseModel):
    bucket: str
    key: str
    contentType: Optional[str] = None
    expiresAt: Optional[int] = None
    correlationId: Optional[str] = None

class CreditRequest(BaseModel):
    userId: int = Field(..., alias="userId")
    cartTotal: Optional[float] = Field(None, alias="cartTotal")

    @root_validator(pre=True)
    def accept_amount_alias(cls, values: Dict[str, Any]):
        if "cartTotal" not in values and "amount" in values:
            values["cartTotal"] = values["amount"]
        return values

    class Config:
        allow_population_by_field_name = True
        anystr_strip_whitespace = True

class CreditResponse(BaseModel):
    approved: bool
    score: int
    reason: Optional[str] = None

    