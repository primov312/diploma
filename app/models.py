from pydantic import BaseModel

class CreditRequest(BaseModel):
    userId: int
    cartTotal: float

class CreditResponse(BaseModel):
    approved: bool
    score: int
    reason: str