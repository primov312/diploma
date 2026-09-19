# app/main.py
"""Rocket Credit - stateless credit analysis.

    GET  /health   liveness + policy/model versions (no auth; used by Compose)
    GET  /policy   the active versioned policy (auth)
    POST /score    validated feature bundle -> explained decision (auth)

The service keeps no state between requests. The Java backend owns
persistence and sends only derived, non-identifying features.
"""
from __future__ import annotations

import hmac
from functools import lru_cache
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, status

from app.ai import Model, load_model, predict
from app.logging import get_logger, log_decision
from app.models import ScoreRequest, ScoreResponse
from app.policy import Policy, load_policy
from app.rules.combiner import combine
from app.settings import Settings

app = FastAPI(title="Rocket Credit - Credit Analysis", version="2.0.0")
logger = get_logger("credit-analysis")


@lru_cache
def get_settings() -> Settings:
    return Settings()


@lru_cache
def get_policy() -> Policy:
    return load_policy(get_settings().policy_path)


@lru_cache
def get_model() -> Optional[Model]:
    """Trusted local artifact only; a missing file simply disables AI (rules keep working)."""
    try:
        model = load_model(get_settings().model_path)
    except Exception as e:  # corrupt artifact must not take the rules down
        logger.error("model artifact could not be loaded: %s", e)
        return None
    if model is None:
        logger.warning("no model artifact at %s; AI requests fall back to rules", get_settings().model_path)
    return model


def require_token(
    x_analysis_token: Optional[str] = Header(default=None),
    cfg: Settings = Depends(get_settings),
) -> None:
    """Simple backend-to-analysis authentication: a shared secret from the
    environment, compared in constant time. The analysis port is not published
    outside the Compose network, so this only guards against another container
    or a misconfigured deployment."""
    if not cfg.shared_secret:
        return
    if x_analysis_token is None or not hmac.compare_digest(x_analysis_token, cfg.shared_secret):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid analysis token")


@app.get("/health")
def health(policy: Policy = Depends(get_policy), model: Optional[Model] = Depends(get_model)):
    return {
        "status": "ok",
        "policyVersion": policy.version,
        "modelVersion": model.version if model else None,
        "modelLoaded": model is not None,
    }


@app.get("/policy", dependencies=[Depends(require_token)])
def read_policy(policy: Policy = Depends(get_policy)):
    return policy.as_dict()


@app.post("/score", response_model=ScoreResponse, dependencies=[Depends(require_token)])
def score(req: ScoreRequest, policy: Policy = Depends(get_policy),
          model: Optional[Model] = Depends(get_model)) -> ScoreResponse:
    ai = None
    if req.useAi and model is not None:
        try:
            ai = predict(model, req)
        except Exception as e:  # e.g. a section is missing -> rules only, recorded as UNAVAILABLE
            logger.warning("ai inference failed, using rules only: %s", e)
    # when useAi is False the model is never invoked
    result = combine(req, policy, ai=ai)
    log_decision(logger, request=req, result=result)
    return result
