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
def health(policy: Policy = Depends(get_policy)):
    return {
        "status": "ok",
        "policyVersion": policy.version,
        "modelVersion": None,  # populated when Step 6 loads a model artifact
        "modelLoaded": False,
    }


@app.get("/policy", dependencies=[Depends(require_token)])
def read_policy(policy: Policy = Depends(get_policy)):
    return policy.as_dict()


@app.post("/score", response_model=ScoreResponse, dependencies=[Depends(require_token)])
def score(req: ScoreRequest, policy: Policy = Depends(get_policy)) -> ScoreResponse:
    # Step 6 will pass a model prediction here when req.useAi is set and a
    # trusted local artifact is loaded; until then AI is reported UNAVAILABLE.
    result = combine(req, policy, ai=None)
    log_decision(logger, request=req, result=result)
    return result
