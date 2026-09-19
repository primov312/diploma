"""Structured JSON logging (no external dependencies).

Decision logs contain derived features and the outcome only; the service never
sees names, emails or session data, so there is nothing personal to redact.
"""
from __future__ import annotations

import json
import logging
import sys
from typing import Any, Dict

_SKIP = {
    "args", "msg", "levelname", "levelno", "name", "pathname", "filename", "module",
    "exc_info", "exc_text", "stack_info", "lineno", "funcName", "created", "msecs",
    "relativeCreated", "thread", "threadName", "processName", "process", "taskName",
}


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: Dict[str, Any] = {
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        for key, value in record.__dict__.items():
            if key not in _SKIP:
                payload[key] = value
        return json.dumps(payload, ensure_ascii=False, default=str)


def get_logger(name: str = "credit-analysis") -> logging.Logger:
    logger = logging.getLogger(name)
    if not logger.handlers:
        logger.setLevel(logging.INFO)
        handler = logging.StreamHandler(stream=sys.stderr)
        handler.setFormatter(JsonFormatter())
        logger.addHandler(handler)
        logger.propagate = False
    return logger


def log_decision(logger: logging.Logger, *, request: Any, result: Any) -> None:
    try:
        logger.info(
            "credit_decision",
            extra={
                "event": "credit_decision",
                "requestedAmount": str(request.requestedAmount),
                "partnerCap": str(request.partnerCap),
                "useAi": request.useAi,
                "decisionStatus": result.decisionStatus.value,
                "score": result.score,
                "possibleAmount": str(result.possibleAmount),
                "reasons": result.reasons,
                "policyVersion": result.policyVersion,
                "aiStatus": result.aiStatus.value,
                "modelVersion": result.modelVersion,
            },
        )
    except Exception:
        return  # logging must never break the request path
