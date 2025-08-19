from __future__ import annotations

from fastapi import FastAPI, HTTPException, Depends
from functools import lru_cache

from app.settings import Settings
from app.models import CreditRequest, CreditResponse
from app.scoring import score_request
from app.db import save_decision, health_check
from app.events import publish_credit_evaluated
from app.policy import get_current_policy
from app.logging import get_logger, log_decision

app = FastAPI(title="Rocket Credit – Credit Analysis", version="1.0.0")
logger = get_logger("credit-analysis")

@lru_cache
def get_settings() -> Settings:
    # Read env once per process; fast and stable.
    return Settings()

@app.get("/_health")
def health(cfg: Settings = Depends(get_settings)):
    ok = health_check(cfg)
    return {"status": "ok" if ok else "degraded", "db": ok}

@app.get("/_policy")
def read_policy(cfg: Settings = Depends(get_settings)):
    pol = get_current_policy(cfg)
    return {
        "name": pol.policy_name,
        "version": pol.version,
        "valid_from": pol.valid_from,
        "weights": pol.weights,
        "thresholds": pol.thresholds,
        "flags": pol.flags,
    }

@app.post("/creditscore", response_model=CreditResponse)
async def creditscore(req: CreditRequest, cfg: Settings = Depends(get_settings)):
    try:
        public, audit = await score_request(req, cfg)
    except Exception as e:
        # Bubble up as 502 so the gateway can map it
        raise HTTPException(status_code=502, detail=f"scoring_failed: {e}")

    # Best-effort side effects (never break the response)
    try:
        save_decision(req, public, audit, cfg)
    except Exception:
        pass
    try:
        publish_credit_evaluated(req, public, audit, cfg)
    except Exception:
        pass

    try:
        pol = get_current_policy(cfg)
        log_decision(
            logger,
            user_id=req.userId,
            cart_total=req.cartTotal,
            public=public,
            audit=audit,
            policy_version=pol.version,
        )
    except Exception:
        pass

    return public