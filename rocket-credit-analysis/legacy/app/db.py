"""
Responsibilities:
- Create a SQLAlchemy Engine (lazy, singleton).
- Insert a decision row into `credit_requests`, including explainability JSONB.
- Lightweight health check.
"""

from __future__ import annotations
import json
from typing import Optional

from sqlalchemy import create_engine, text, bindparam
from sqlalchemy.engine import Engine
from sqlalchemy.dialects.postgresql import JSONB  # <-- add this

from app.models import CreditRequest, CreditResponse
from app.settings import Settings
import logging

log = logging.getLogger(__name__)

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
            pool_pre_ping=True,
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

def save_decision(req, public, audit, cfg) -> None:
    try:
        eng = get_engine(cfg)

        # Map to allowed V1 CHECK values
        if public.approved:
            decision_str = "APPROVED"
        elif (public.reason or "").upper() == "REVIEW":
            decision_str = "PENDING"     # keep CHECK(decision IN (...))
        else:
            decision_str = "DENIED"

        reasons_obj = getattr(audit, "reasons", []) or []
        factors_obj = getattr(audit, "factors", {}) or {}
        stmt = text("""
            INSERT INTO credit_requests
              (user_id, user_external_id, request_amount, score, decision, reasons, factors)
            VALUES
              (gen_random_uuid(), :user_external_id, :request_amount, :score, :decision, :reasons, :factors)
        """).bindparams(
            bindparam("reasons", type_=JSONB),
            bindparam("factors", type_=JSONB),
        )

        with eng.begin() as conn:
            conn.execute(
                stmt,
                {
                    "user_external_id": req.userId,             # BIGINT (V3)
                    "request_amount": req.cartTotal,            # DECIMAL(10,2)
                    "score": int(getattr(audit, "score_int", public.score)),
                    "decision": decision_str,
                    "reasons": reasons_obj,                     # JSONB
                    "factors": factors_obj,                     # JSONB
                },
            )
    except Exception as e:
        # Log if you want visibility; keep request path resilient
        import logging; logging.getLogger(__name__).warning("save_decision skipped: %s", e, exc_info=True)
        return