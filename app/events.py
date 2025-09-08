"""
Event publishing for credit decisions (best-effort, non-blocking).
Uses confluent-kafka if configured; otherwise becomes a no-op.

Publish payload example to topic `credit.evaluated`:
{
  "userId": 123,
  "approved": true,
  "score": 74,
  "finalReason": "OK",
  "reasons": [...],
  "factors": {...},
  "policy": {"name":"default","version":2,"weights":{...},"thresholds":{...}},
  "occurredAt": "2025-08-18T09:12:03Z"
}
"""

from __future__ import annotations
import json
from datetime import datetime, timezone
from typing import Optional

from app.models import CreditRequest, CreditResponse
from app.settings import Settings
from app.policy import get_current_policy

try:
    from confluent_kafka import Producer  # type: ignore
except Exception:  # pragma: no cover - optional path
    Producer = None  # type: ignore


_PRODUCER: Optional["Producer"] = None


def _build_producer(cfg: Settings) -> Optional["Producer"]:
    if Producer is None:
        return None
    bootstrap = getattr(cfg, "kafka_bootstrap", None) or ""
    if not bootstrap:
        return None
    # Minimal, safe producer configuration
    conf = {
        "bootstrap.servers": bootstrap,
        "enable.idempotence": True,
        "compression.type": "zstd",
        "linger.ms": 5,
        "batch.num.messages": 1000,
        "message.send.max.retries": 3,
        "request.timeout.ms": 8000,
        "socket.timeout.ms": 8000,
        # TLS/SASL can be added here if present in Settings
    }
    try:
        return Producer(conf)
    except Exception:
        return None


def _get_producer(cfg: Settings) -> Optional["Producer"]:
    global _PRODUCER
    if _PRODUCER is None:
        _PRODUCER = _build_producer(cfg)
    return _PRODUCER


def publish_credit_evaluated(
    req: CreditRequest,
    public: CreditResponse,
    audit: "object",
    cfg: Settings,
) -> None:
    """
    Fire-and-forget event for downstream consumers (notifications/analytics).
    Never raises; silently no-ops if Kafka is not configured.
    """
    if not getattr(cfg, "enable_events", False) or not getattr(cfg, "kafka_bootstrap", None):
        return
    producer = _get_producer(cfg)
    if producer is None:
        return

    try:
        pol = get_current_policy(cfg)  # cached; cheap
        payload = {
            "userId": req.userId,
            "approved": public.approved,
            "score": public.score,
            "finalReason": public.reason,
            "reasons": getattr(audit, "reasons", []),
            "factors": getattr(audit, "factors", {}),
            "policy": {
                "name": pol.policy_name,
                "version": pol.version,
                "weights": pol.weights,
                "thresholds": pol.thresholds,
            },
            "occurredAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }

        topic = getattr(cfg, "kafka_topic_credit_evaluated", "credit.evaluated")
        key = str(req.userId).encode("utf-8")
        val = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")

        # Delivery callback to avoid blocking
        def _cb(err, _msg):  # pragma: no cover
            # Intentionally silent; wire real logging here if desired.
            return

        producer.produce(topic=topic, key=key, value=val, on_delivery=_cb)
        producer.poll(0)  # trigger delivery callbacks
    except Exception:
        # Never break the hot path on event failure.
        return