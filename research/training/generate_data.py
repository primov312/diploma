"""Generate reproducible synthetic customers and applications with default labels.

Usage: python generate_data.py [--customers 4000] [--seed 42] [--out data/]

Label generation (documented assumption, see research/results/evaluation.md):
    latent = -1.6
             - 0.035 * accountAgeMonths
             - 1.6   * partnerOnTimeRatio
             + 2.2   * partnerRefundRate
             - 0.05  * partnerOrders12m
             - 0.02  * partnerTenureMonths
             - 0.5   * profileComplete - 0.3 * emailVerified
             + 1.4   * utilization                (requested / (disposable*3))
             + 1.2   * [disposable <= 0]
             + 0.4   * sqrt(requestedAmount / 1000)
             + N(0, 0.7)
    P(default) = sigmoid(latent);  default ~ Bernoulli(P)

It is a hand-written latent-risk model with noise, not the rule policy: the rules never see
`utilization` continuously, weigh order value and tenure differently, and have no randomness.
Labels are therefore correlated with, but not determined by, the rule decision.
"""
from __future__ import annotations

import argparse
import csv
import math
import pathlib
import random


def sigmoid(x: float) -> float:
    return 1.0 / (1.0 + math.exp(-x))


def make_customer(rng: random.Random, cid: int) -> dict:
    segment = rng.random()
    if segment < 0.35:      # established
        age = rng.randint(12, 60); complete = rng.random() < 0.9; verified = rng.random() < 0.95
        orders = rng.randint(4, 24); ontime = min(1.0, rng.gauss(0.95, 0.05)); refund = max(0.0, rng.gauss(0.04, 0.04))
        tenure = rng.randint(6, 48); income = rng.uniform(2800, 7000)
    elif segment < 0.75:    # average
        age = rng.randint(3, 24); complete = rng.random() < 0.6; verified = rng.random() < 0.8
        orders = rng.randint(1, 10); ontime = min(1.0, rng.gauss(0.85, 0.12)); refund = max(0.0, rng.gauss(0.10, 0.08))
        tenure = rng.randint(1, 18); income = rng.uniform(1800, 4500)
    else:                   # new / thin
        age = rng.randint(0, 6); complete = rng.random() < 0.3; verified = rng.random() < 0.6
        orders = rng.randint(0, 3); ontime = min(1.0, max(0.0, rng.gauss(0.7, 0.25))) if orders else 0.0
        refund = max(0.0, rng.gauss(0.12, 0.12)) if orders else 0.0
        tenure = rng.randint(0, 4); income = rng.uniform(1200, 3200)
    expenses = income * rng.uniform(0.55, 0.95)
    obligations = income * rng.uniform(0.0, 0.2)
    aov = max(5.0, rng.lognormvariate(4.2, 0.7))
    return {
        "customerId": cid,
        "accountAgeMonths": age, "profileComplete": complete, "emailVerified": verified,
        "partnerOrders12m": orders, "partnerAvgOrderValue": round(aov, 2),
        "partnerRefundRate": round(min(1.0, refund), 4), "partnerOnTimeRatio": round(max(0.0, ontime), 4),
        "partnerTenureMonths": tenure, "totalOrders12m": orders + rng.randint(0, 12),
        "monthlyIncome": round(income, 2), "monthlyExpenses": round(expenses, 2), "monthlyObligations": round(obligations, 2),
    }


def label(rng: random.Random, c: dict, requested: float) -> tuple[int, float]:
    disposable = c["monthlyIncome"] - c["monthlyExpenses"] - c["monthlyObligations"]
    util = requested / max(1.0, disposable * 3.0)
    latent = (-1.6 - 0.035 * c["accountAgeMonths"] - 1.6 * c["partnerOnTimeRatio"] + 2.2 * c["partnerRefundRate"]
              - 0.05 * c["partnerOrders12m"] - 0.02 * c["partnerTenureMonths"]
              - 0.5 * c["profileComplete"] - 0.3 * c["emailVerified"]
              + 1.4 * min(3.0, util) + 1.2 * (1.0 if disposable <= 0 else 0.0)
              + 0.4 * math.sqrt(requested / 1000.0) + rng.gauss(0.0, 0.7))
    p = sigmoid(latent)
    return (1 if rng.random() < p else 0), p


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--customers", type=int, default=4000)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--out", default=str(pathlib.Path(__file__).parent / "data"))
    args = ap.parse_args()

    rng = random.Random(args.seed)
    out = pathlib.Path(args.out); out.mkdir(parents=True, exist_ok=True)
    rows = []
    for cid in range(args.customers):
        c = make_customer(rng, cid)
        for _ in range(rng.randint(1, 3)):
            requested = round(rng.choice([rng.uniform(20, 300), rng.uniform(100, 900), rng.uniform(300, 1500)]), 2)
            y, p = label(rng, c, requested)
            rows.append({**c, "requestedAmount": requested, "default": y, "_latentP": round(p, 4)})

    # split by customer, not by row: 70% of customer ids train, 30% test
    ids = list(range(args.customers)); rng.shuffle(ids)
    cut = int(0.7 * len(ids)); train_ids = set(ids[:cut])
    fields = list(rows[0].keys())
    for name, keep in (("train", lambda r: r["customerId"] in train_ids), ("test", lambda r: r["customerId"] not in train_ids)):
        with (out / f"{name}.csv").open("w", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=fields); w.writeheader()
            n = 0
            for r in rows:
                if keep(r):
                    w.writerow(r); n += 1
        print(f"{name}: {n} applications")
    print(f"default rate: {sum(r['default'] for r in rows) / len(rows):.3f}")


if __name__ == "__main__":
    main()
