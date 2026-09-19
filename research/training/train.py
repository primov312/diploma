"""Fit one logistic-regression model on train.csv and write the serving artifact.

Usage: python train.py [--data data/] [--out ../../rocket-credit-analysis/app/model/model-v1.json]

Pure numpy: standardize features, then L2-regularised logistic regression by Newton's method.
The artifact holds everything the analysis service needs to reproduce predictions without
any ML library: feature names, means, standard deviations, coefficients, intercept, version.
"""
from __future__ import annotations

import argparse
import csv
import json
import pathlib
from datetime import date

import numpy as np

from common import FEATURES, featurize

MODEL_VERSION = "logreg-v1"


def load(path: pathlib.Path) -> tuple[np.ndarray, np.ndarray]:
    with path.open() as fh:
        rows = list(csv.DictReader(fh))
    for r in rows:
        r["profileComplete"] = r["profileComplete"] == "True"
        r["emailVerified"] = r["emailVerified"] == "True"
    X = np.array([featurize(r) for r in rows], dtype=float)
    y = np.array([int(r["default"]) for r in rows], dtype=float)
    return X, y


def fit(X: np.ndarray, y: np.ndarray, l2: float = 1.0, iters: int = 50) -> tuple[np.ndarray, float]:
    n, d = X.shape
    Xb = np.hstack([X, np.ones((n, 1))])
    w = np.zeros(d + 1)
    reg = np.eye(d + 1) * l2; reg[d, d] = 0.0   # do not regularise the intercept
    for _ in range(iters):
        z = Xb @ w
        p = 1.0 / (1.0 + np.exp(-z))
        grad = Xb.T @ (p - y) + reg @ w
        W = p * (1 - p)
        H = (Xb * W[:, None]).T @ Xb + reg
        step = np.linalg.solve(H, grad)
        w -= step
        if np.max(np.abs(step)) < 1e-8:
            break
    return w[:d], float(w[d])


def main() -> None:
    ap = argparse.ArgumentParser()
    here = pathlib.Path(__file__).parent
    ap.add_argument("--data", default=str(here / "data"))
    ap.add_argument("--out", default=str(here / "../../rocket-credit-analysis/app/model/model-v1.json"))
    args = ap.parse_args()

    X, y = load(pathlib.Path(args.data) / "train.csv")
    mean = X.mean(axis=0); std = X.std(axis=0); std[std == 0] = 1.0
    coef, intercept = fit((X - mean) / std, y)

    artifact = {
        "modelVersion": MODEL_VERSION,
        "trainedOn": date.today().isoformat(),
        "algorithm": "logistic regression, L2=1.0, Newton, standardized inputs",
        "target": "P(synthetic default) for an application; aiScore = 1 - risk",
        "features": FEATURES,
        "mean": [round(float(v), 6) for v in mean],
        "std": [round(float(v), 6) for v in std],
        "coef": [round(float(v), 6) for v in coef],
        "intercept": round(intercept, 6),
        "trainingRows": int(len(y)),
        "trainingDefaultRate": round(float(y.mean()), 4),
    }
    out = pathlib.Path(args.out).resolve(); out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(artifact, indent=2) + "\n")
    print(f"wrote {out}")
    for name, c in sorted(zip(FEATURES, coef), key=lambda t: -abs(t[1])):
        print(f"  {name:20s} {c:+.3f}")


if __name__ == "__main__":
    main()
