"""
Responsibilities:
- Create a SQLAlchemy Engine (lazy, singleton).
- Insert a decision row into `credit_requests`, including explainability JSONB.
- Lightweight health check.
"""

from __future__ import annotations
import json
from typing import Optional

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

from app.models import CreditRequest, CreditResponse
from app.settings import Settings


_ENGINE: Optional[Engine] = None


def get_engine(cfg: Settings) -> Engine:
    """
    Lazily create and cache a SQLAlchemy engine using the URL from Settings.
    """
    global _ENGINE
    if _ENGINE is None:
        _ENGINE = create_engine(
            cfg.sqlalchemy_url,
            future=True,
            pool_pre_ping=True,       # proactively validate connections
            pool_size=5,
            max_overflow=10,
        )
    return _ENGINE


def health_check(cfg: Settings) -> bool:
    """
    Simple DB health probe.
    """
    try:
        eng = get_engine(cfg)
        with eng.connect() as conn:
            conn.execute(text("SELECT 1"))
        return True
    except Exception:
        return False


def save_decision(
    req: CreditRequest,
    public: CreditResponse,
    audit: "object",  # duck-typed (expects .reasons, .factors, .score_int, .final_reason, .approved)
    cfg: Settings,
) -> None:
    """
    Persist the decision into credit_requests. This function is intentionally
    tolerant: it should never crash the request path.

    Expected table columns (from V1..V4 migrations):
      - user_external_id BIGINT
      - request_amount NUMERIC
      - score INTEGER
      - decision VARCHAR
      - reasons JSONB
      - factors JSONB
      - created_at TIMESTAMPTZ DEFAULT now()  (if present)
    """
    try:
        eng = get_engine(cfg)
        decision_str = (
            "APPROVED" if public.approved else ("REVIEW" if public.reason == "REVIEW" else "DENIED")
        )

        # JSONB params need serialized strings when using text()
        reasons_json = json.dumps(getattr(audit, "reasons", []) or [])
        factors_json = json.dumps(getattr(audit, "factors", {}) or {})

        with eng.begin() as conn:
            conn.execute(
                text(
                    """
                    INSERT INTO credit_requests
                      (user_external_id, request_amount, score, decision, reasons, factors)
                    VALUES
                      (:user_external_id, :request_amount, :score, :decision, :reasons::jsonb, :factors::jsonb)
                    """
                ),
                dict(
                    user_external_id=req.userId,
                    request_amount=req.cartTotal,
                    score=int(getattr(audit, "score_int", public.score)),
                    decision=decision_str,
                    reasons=reasons_json,
                    factors=factors_json,
                ),
            )
    except Exception:
        # Swallow errors in persistence to keep the hot path resilient.
        # Rely on app/logging.py's logger in the FastAPI layer to record failures if desired.
        return