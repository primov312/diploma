# main.py
from fastapi import FastAPI, HTTPException, Depends
from functools import lru_cache

from app.settings import Settings
from app.models import CreditRequest, CreditResponse
from app.scoring import score_request
from app.db import save_decision, health_check
from app.events import publish_credit_evaluated
from app.policy import get_current_policy
from app.logging import get_logger, log_decision
from app.claimcheck import fetch_features_from_claim  # <-- NEW

app = FastAPI(title="Rocket Credit – Credit Analysis", version="1.0.0")
logger = get_logger("credit-analysis")

@lru_cache
def get_settings() -> Settings:
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
    # ---- Compatibility shim: allow either `cartTotal` or `amount`
    if req.cartTotal is None and req.amount is not None:
        # populate cartTotal so downstream code reads the same field
        req.cartTotal = req.amount

    # ---- Option B: Claim-Check / direct features
    resolved_features = None
    try:
        if req.features:
            resolved_features = req.features
        elif req.featureClaim:
            resolved_features = fetch_features_from_claim(req.featureClaim, cfg)
    except Exception as e:
        # don't fail the request; just log and proceed with default path
        logger.warning("Claim-Check feature fetch failed: %s", e)

    try:
        # pass preloaded features if available; scoring falls back to UDS otherwise
        public, audit = await score_request(req, cfg, features_override=resolved_features)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"scoring_failed: {e}")

    # Side-effects are best-effort
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