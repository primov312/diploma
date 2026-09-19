# app/ai.py
"""Inference for the offline-trained logistic-regression model.

The artifact (app/model/model-v1.json, produced by research/training/train.py) is a trusted
project file loaded once at startup. No ML library is needed at serving time: the model is
standardization + linear combination + sigmoid. Contributions are the per-feature log-odds
terms, which is what makes a logistic model explainable feature by feature.
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional

from app.models import ScoreRequest


@dataclass(frozen=True)
class Model:
    version: str
    features: List[str]
    mean: List[float]
    std: List[float]
    coef: List[float]
    intercept: float


@dataclass(frozen=True)
class AiPrediction:
    risk: float                       # P(synthetic default), 0..1
    model_version: str
    contributions: Dict[str, float]   # feature -> log-odds contribution (positive = riskier)


def load_model(path: str | Path) -> Optional[Model]:
    p = Path(path)
    if not p.exists():
        return None
    raw = json.loads(p.read_text(encoding="utf-8"))
    n = len(raw["features"])
    if not (len(raw["mean"]) == len(raw["std"]) == len(raw["coef"]) == n):
        raise ValueError("model artifact is inconsistent")
    return Model(raw["modelVersion"], list(raw["features"]), list(map(float, raw["mean"])),
                 list(map(float, raw["std"])), list(map(float, raw["coef"])), float(raw["intercept"]))


def featurize(req: ScoreRequest) -> Dict[str, float]:
    """Same mapping as research/training/common.py, applied to the API request."""
    p, h, f = req.profile, req.history, req.finance
    if p is None or h is None or f is None:
        raise ValueError("AI needs profile, history and finance sections")
    income = float(f.monthlyIncome)
    disposable = income - float(f.monthlyExpenses) - float(f.monthlyObligations)
    ratio = disposable / income if income > 0 else -1.0
    util = float(req.requestedAmount) / max(1.0, disposable * 3.0)
    return {
        "accountAgeMonths": float(p.accountAgeMonths),
        "profileComplete": 1.0 if p.profileComplete else 0.0,
        "emailVerified": 1.0 if p.emailVerified else 0.0,
        "partnerOrders12m": float(h.partnerOrders12m),
        "logAvgOrderValue": math.log1p(float(h.partnerAvgOrderValue)),
        "partnerRefundRate": float(h.partnerRefundRate),
        "partnerOnTimeRatio": float(h.partnerOnTimeRatio),
        "partnerTenureMonths": float(h.partnerTenureMonths),
        "totalOrders12m": float(h.totalOrders12m),
        "disposableRatio": max(-1.0, min(1.0, ratio)),
        "logIncome": math.log1p(max(0.0, income)),
        "utilization": max(0.0, min(3.0, util)),
    }


def predict(model: Model, req: ScoreRequest) -> AiPrediction:
    x = featurize(req)
    z = model.intercept
    contributions: Dict[str, float] = {}
    for name, mu, sd, w in zip(model.features, model.mean, model.std, model.coef):
        term = w * (x[name] - mu) / (sd if sd else 1.0)
        contributions[name] = round(term, 4)
        z += term
    risk = 1.0 / (1.0 + math.exp(-z))
    return AiPrediction(risk=round(risk, 4), model_version=model.version, contributions=contributions)
