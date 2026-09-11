"""
Structured JSON logging helpers.
- Minimal JSON formatter (no external deps).
- get_logger(name) returns a logger configured for JSON output.
- log_decision(...) emits a single structured line per decision.

Integrates cleanly with Uvicorn's logging, but can also be used standalone.
"""

from __future__ import annotations
import json
import logging
import sys
from typing import Any, Dict, Optional


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: Dict[str, Any] = {
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        # Pull extra keys (added via logger.info(msg, extra={...}))
        for key, value in record.__dict__.items():
            if key in ("args", "msg", "levelname", "levelno", "name", "pathname",
                       "filename", "module", "exc_info", "exc_text", "stack_info",
                       "lineno", "funcName", "created", "msecs", "relativeCreated",
                       "thread", "threadName", "processName", "process"):
                continue
            if key == "exc_info" and value:
                continue
            payload[key] = value
        return json.dumps(payload, ensure_ascii=False)


def get_logger(name: str = "credit-analysis") -> logging.Logger:
    """
    Returns a logger configured for JSON output to stderr.
    Safe to call multiple times; handler will be added only once.
    """
    logger = logging.getLogger(name)
    if not logger.handlers:
        logger.setLevel(logging.INFO)
        handler = logging.StreamHandler(stream=sys.stderr)
        handler.setFormatter(JsonFormatter())
        logger.addHandler(handler)
        logger.propagate = False
    return logger


def log_decision(
    logger: logging.Logger,
    *,
    user_id: int,
    cart_total: float,
    public: "object",
    audit: "object",
    policy_version: Optional[int] = None,
    trace_id: Optional[str] = None,
) -> None:
    """
    Emit a single structured log line summarizing a decision.
    """
    try:
        logger.info(
            "credit_decision",
            extra={
                "event": "credit_decision",
                "trace_id": trace_id,
                "user_id": user_id,
                "cart_total": cart_total,
                "approved": getattr(public, "approved", None),
                "score": getattr(public, "score", None),
                "final_reason": getattr(public, "reason", None),
                "reasons": getattr(audit, "reasons", []),
                "factors": getattr(audit, "factors", {}),
                "thresholds": getattr(audit, "thresholds", {}),
                "affordability_ok": getattr(audit, "affordability_ok", None),
                "policy_version": policy_version,
            },
        )
    except Exception:
        # Logging must never crash the request path.
        return