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
    # Gateway normally sends a claim-check reference. Keeping it optional also
    # supports the documented direct API, where analysis loads the feature
    # bundle from User Data itself.
    featureClaim: Optional[ClaimRef] = None

    @root_validator(pre=True)
    def accept_amount_alias(cls, values: Dict[str, Any]):
        if "cartTotal" not in values and "amount" in values:
            values["cartTotal"] = values["amount"]
        # Accept various keys for the claim
        for k in ("feature", "claim", "claimRef", "claim_ref"):
            if k in values and "featureClaim" not in values:
                values["featureClaim"] = values[k]

        # Optionally, accept alt property names inside the claim
        feat = values.get("featureClaim")
        if isinstance(feat, dict):
            # map alternative spellings if present
            if "content_type" in feat and "contentType" not in feat:
                feat["contentType"] = feat["content_type"]
            if "expiry" in feat and "expiresAt" not in feat:
                feat["expiresAt"] = feat["expiry"]

        return values

    class Config:
        allow_population_by_field_name = True
        anystr_strip_whitespace = True

class CreditResponse(BaseModel):
    approved: bool
    score: int
    reason: Optional[str] = None
