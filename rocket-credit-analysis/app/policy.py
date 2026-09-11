from __future__ import annotations
import time
from dataclasses import dataclass
from typing import Dict, Optional

from sqlalchemy import create_engine, text
from app.settings import Settings

@dataclass
class CreditPolicy:
    weights: Dict[str, float]
    thresholds: Dict[str, float]
    flags: Dict[str, bool]
    version: int
    policy_name: str
    valid_from: str

# in-process cache
_cache: Optional[CreditPolicy] = None
_cache_ts: float = 0.0

def _renormalize(weights: Dict[str, float]) -> Dict[str, float]:
    # Keep only known keys and renormalize to sum=1.0
    keys = ["rocket", "partner", "amount"]  # live factors today
    filt = {k: max(0.0, float(weights.get(k, 0.0))) for k in keys}
    s = sum(filt.values()) or 1.0
    return {k: v / s for k, v in filt.items()}

def _validate(policy: CreditPolicy) -> CreditPolicy:
    w = _renormalize(policy.weights)
    approve = float(policy.thresholds.get("approve", 0.60))
    review  = float(policy.thresholds.get("review", 0.50))
    # clamp sensible bounds
    approve = min(max(approve, 0.0), 1.0)
    review  = min(max(review,  0.0), approve)
    return CreditPolicy(weights=w,
                        thresholds={"approve": approve, "review": review},
                        flags=policy.flags or {},
                        version=policy.version,
                        policy_name=policy.policy_name,
                        valid_from=policy.valid_from)

def get_current_policy(cfg: Settings, *, force_refresh: bool = False) -> CreditPolicy:
    """
    Load latest enabled policy for 'default' where valid_from <= now(),
    with small TTL cache to avoid hammering the DB.
    """
    global _cache, _cache_ts
    ttl = getattr(cfg, "policy_cache_ttl_seconds", 30)
    now = time.time()
    if _cache and not force_refresh and (now - _cache_ts) < ttl:
        return _cache

    engine = create_engine(cfg.sqlalchemy_url, future=True)
    with engine.connect() as conn:
        row = conn.execute(text("""
            SELECT policy_name, version, weights, thresholds, flags, valid_from
            FROM credit_policy_versions
            WHERE policy_name = :name AND enabled = TRUE AND valid_from <= now()
            ORDER BY valid_from DESC, version DESC
            LIMIT 1
        """), {"name": "default"}).mappings().first()

    if not row:
        # Fallback to code defaults in Settings
        pol = CreditPolicy(
            weights={"rocket":0.5, "partner":0.3, "amount":0.2},
            thresholds={"approve": cfg.approve_threshold, "review": cfg.review_threshold},
            flags={"enable_social": False},
            version=0,
            policy_name="default",
            valid_from="1970-01-01T00:00:00Z",
        )
    else:
        pol = CreditPolicy(
            weights=row["weights"],
            thresholds=row["thresholds"],
            flags=row["flags"] or {},
            version=row["version"],
            policy_name=row["policy_name"],
            valid_from=str(row["valid_from"]),
        )

    pol = _validate(pol)
    _cache, _cache_ts = pol, now
    return pol