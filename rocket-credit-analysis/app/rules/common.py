# app/rules/common.py
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional


def clamp01(x: float) -> float:
    return max(0.0, min(1.0, float(x)))


def safe_minmax(x: float, lo: float, hi: float) -> float:
    """Linear scaling of x from [lo, hi] onto [0, 1]. Degenerate ranges map to
    the midpoint instead of dividing by zero."""
    if hi <= lo:
        return 0.5
    return clamp01((float(x) - lo) / (hi - lo))


@dataclass
class RuleResult:
    score: Optional[float]  # None when the rule had no evidence
    reasons: List[str] = field(default_factory=list)
    details: Dict[str, float | int | bool | str | None] = field(default_factory=dict)
