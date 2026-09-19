# app/policy.py
"""Versioned local scoring policy.

The legacy service read weights from a `credit_policy_versions` table. The
diploma runtime has no analysis database, so the policy is a JSON file shipped
with the code and loaded once at startup. Its `version` is returned with every
decision and stored by the backend, so a saved result always identifies the
exact policy that produced it.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict


@dataclass(frozen=True)
class Policy:
    version: str
    weights: Dict[str, float]
    thresholds: Dict[str, float]
    affordability: Dict[str, float]
    profile: Dict[str, Any]
    history: Dict[str, Any]

    def as_dict(self) -> Dict[str, Any]:
        return {
            "version": self.version,
            "weights": self.weights,
            "thresholds": self.thresholds,
            "affordability": self.affordability,
            "profile": self.profile,
            "history": self.history,
        }


def _validate(raw: Dict[str, Any]) -> Policy:
    weights = {k: float(v) for k, v in raw["weights"].items()}
    for pair in (("history", "profile"), ("rules", "ai")):
        total = sum(weights[k] for k in pair)
        if abs(total - 1.0) > 1e-6:
            raise ValueError(f"policy weights {pair} must sum to 1.0, got {total}")
    thresholds = {k: float(v) for k, v in raw["thresholds"].items()}
    if not 0.0 <= thresholds["review"] <= thresholds["approve"] <= 1.0:
        raise ValueError("policy thresholds must satisfy 0 <= review <= approve <= 1")
    affordability = {k: float(v) for k, v in raw["affordability"].items()}
    if affordability["demoMultiplier"] <= 0:
        raise ValueError("demoMultiplier must be positive")
    return Policy(
        version=str(raw["version"]),
        weights=weights,
        thresholds=thresholds,
        affordability=affordability,
        profile=dict(raw.get("profile", {})),
        history=dict(raw.get("history", {})),
    )


def load_policy(path: str | Path) -> Policy:
    with Path(path).open(encoding="utf-8") as fh:
        return _validate(json.load(fh))
