"""Evaluate rules-only vs rules+AI on the held-out synthetic customers (test.csv).

Usage: python evaluate.py [--data data/] [--results ../results/]

Both modes run through the real analysis code (app.rules.combiner) with the real policy and
model artifact, so the numbers describe exactly what the service does. Positive class =
synthetic default. "Predicted risky" = decision is not APPROVED.
"""
from __future__ import annotations

import argparse
import csv
import json
import pathlib
import sys
from collections import Counter
from decimal import Decimal

import numpy as np

HERE = pathlib.Path(__file__).parent
sys.path.insert(0, str((HERE / "../../rocket-credit-analysis").resolve()))

from app.ai import load_model, predict            # noqa: E402
from app.models import ScoreRequest              # noqa: E402
from app.policy import load_policy               # noqa: E402
from app.rules.combiner import combine           # noqa: E402

PARTNER_CAP = Decimal("1500.00")   # synthetic data has no partner; use the largest demo cap


def to_request(r: dict, use_ai: bool) -> ScoreRequest:
    return ScoreRequest(
        requestedAmount=Decimal(r["requestedAmount"]), partnerCap=PARTNER_CAP, useAi=use_ai,
        observedAt="2026-09-19T00:00:00Z",
        profile={"accountAgeMonths": int(r["accountAgeMonths"]), "profileComplete": r["profileComplete"] == "True",
                 "emailVerified": r["emailVerified"] == "True"},
        history={"partnerOrders12m": int(r["partnerOrders12m"]), "partnerAvgOrderValue": Decimal(r["partnerAvgOrderValue"]),
                 "partnerRefundRate": float(r["partnerRefundRate"]), "partnerOnTimeRatio": float(r["partnerOnTimeRatio"]),
                 "partnerTenureMonths": int(r["partnerTenureMonths"]), "totalOrders12m": int(r["totalOrders12m"])},
        finance={"monthlyIncome": Decimal(r["monthlyIncome"]), "monthlyExpenses": Decimal(r["monthlyExpenses"]),
                 "monthlyObligations": Decimal(r["monthlyObligations"])},
    )


def auc(scores: np.ndarray, labels: np.ndarray) -> float:
    """ROC-AUC via the rank statistic (ties get average rank)."""
    order = np.argsort(scores)
    ranks = np.empty(len(scores)); ranks[order] = np.arange(1, len(scores) + 1)
    # average ranks for ties
    s_sorted = scores[order]
    i = 0
    while i < len(s_sorted):
        j = i
        while j + 1 < len(s_sorted) and s_sorted[j + 1] == s_sorted[i]:
            j += 1
        if j > i:
            ranks[order[i:j + 1]] = (i + 1 + j + 1) / 2
        i = j + 1
    pos = labels == 1
    n_pos, n_neg = pos.sum(), (~pos).sum()
    return float((ranks[pos].sum() - n_pos * (n_pos + 1) / 2) / (n_pos * n_neg))


def confusion(pred_risky: np.ndarray, labels: np.ndarray) -> dict:
    tp = int(((pred_risky == 1) & (labels == 1)).sum()); fp = int(((pred_risky == 1) & (labels == 0)).sum())
    fn = int(((pred_risky == 0) & (labels == 1)).sum()); tn = int(((pred_risky == 0) & (labels == 0)).sum())
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    approved = fn + tn
    return {"tp": tp, "fp": fp, "fn": fn, "tn": tn,
            "precision_default": round(precision, 4), "recall_default": round(recall, 4),
            "approval_rate": round(approved / len(labels), 4),
            "default_rate_among_approved": round(fn / approved, 4) if approved else None}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=str(HERE / "data"))
    ap.add_argument("--results", default=str(HERE / "../results"))
    args = ap.parse_args()

    policy = load_policy(str((HERE / "../../rocket-credit-analysis/app/policy.json").resolve()))
    model = load_model(str((HERE / "../../rocket-credit-analysis/app/model/model-v1.json").resolve()))
    assert model is not None

    with (pathlib.Path(args.data) / "test.csv").open() as fh:
        rows = list(csv.DictReader(fh))
    labels = np.array([int(r["default"]) for r in rows])

    rules_score, hybrid_score, ai_risk = [], [], []
    rules_dec, hybrid_dec = [], []
    examples = []
    for r in rows:
        req_rules = to_request(r, False)
        res_rules = combine(req_rules, policy)
        req_ai = to_request(r, True)
        pred = predict(model, req_ai)
        res_hybrid = combine(req_ai, policy, ai=pred)
        rules_score.append(res_rules.score); hybrid_score.append(res_hybrid.score); ai_risk.append(pred.risk)
        rules_dec.append(res_rules.decisionStatus.value); hybrid_dec.append(res_hybrid.decisionStatus.value)
        if res_rules.decisionStatus != res_hybrid.decisionStatus and len(examples) < 8:
            examples.append({"customerId": r["customerId"], "requestedAmount": r["requestedAmount"], "default": int(r["default"]),
                             "rules": res_rules.decisionStatus.value, "rulesScore": res_rules.score,
                             "hybrid": res_hybrid.decisionStatus.value, "hybridScore": res_hybrid.score,
                             "aiRisk": pred.risk, "reasonsHybrid": res_hybrid.reasons})

    rules_score = np.array(rules_score); hybrid_score = np.array(hybrid_score); ai_risk = np.array(ai_risk)
    rules_dec = np.array(rules_dec); hybrid_dec = np.array(hybrid_dec)

    # Affordability gates both modes identically; isolate the score effect on the ungated subset.
    within = np.array([d != "REJECTED" or s >= policy.thresholds["review"] for d, s in zip(rules_dec, rules_score)])
    gated = ~np.isin(rules_dec, ["APPROVED", "REVIEW"]) & (rules_score >= policy.thresholds["approve"])  # rejected purely by amount

    result = {
        "dataset": {"testApplications": int(len(rows)), "testCustomers": len({r["customerId"] for r in rows}),
                    "defaultRate": round(float(labels.mean()), 4), "partnerCapAssumed": str(PARTNER_CAP)},
        "policyVersion": policy.version, "modelVersion": model.version,
        "rocAuc": {"rulesScore": round(auc(1 - rules_score, labels), 4),
                   "hybridScore": round(auc(1 - hybrid_score, labels), 4),
                   "aiRiskAlone": round(auc(ai_risk, labels), 4)},
        "decisions": {
            "rules": {"counts": dict(Counter(rules_dec.tolist())), **confusion((rules_dec != "APPROVED").astype(int), labels)},
            "hybrid": {"counts": dict(Counter(hybrid_dec.tolist())), **confusion((hybrid_dec != "APPROVED").astype(int), labels)},
        },
        "decisionChanged": int((rules_dec != hybrid_dec).sum()),
        "rejectedByAmountOnly": int(gated.sum()),
        "examples": examples,
    }

    out = pathlib.Path(args.results); out.mkdir(parents=True, exist_ok=True)
    (out / "evaluation.json").write_text(json.dumps(result, indent=2) + "\n")
    with (out / "confusion_matrices.csv").open("w", newline="") as fh:
        w = csv.writer(fh); w.writerow(["mode", "tp", "fp", "fn", "tn", "precision_default", "recall_default", "approval_rate", "default_rate_among_approved"])
        for mode in ("rules", "hybrid"):
            d = result["decisions"][mode]
            w.writerow([mode, d["tp"], d["fp"], d["fn"], d["tn"], d["precision_default"], d["recall_default"], d["approval_rate"], d["default_rate_among_approved"]])

    r, h = result["decisions"]["rules"], result["decisions"]["hybrid"]
    md = f"""# Rules vs rules + AI on held-out synthetic customers

Generated by `research/training/evaluate.py` from `data/test.csv` ({result['dataset']['testApplications']} applications,
{result['dataset']['testCustomers']} customers never seen in training, default rate {result['dataset']['defaultRate']:.1%}).
Policy `{policy.version}`, model `{model.version}`. Partner cap assumed {PARTNER_CAP} USD for every row.

## Ranking quality (ROC-AUC against the synthetic default label)

| Score | ROC-AUC |
| --- | ---: |
| rules score alone | {result['rocAuc']['rulesScore']:.3f} |
| hybrid score (0.8 rules + 0.2 AI) | {result['rocAuc']['hybridScore']:.3f} |
| AI risk alone | {result['rocAuc']['aiRiskAlone']:.3f} |

## Decisions (positive class = default; "risky" = not APPROVED)

| Mode | APPROVED | REVIEW | REJECTED | TP | FP | FN | TN | Precision (default) | Recall (default) | Approval rate | Default rate among approved |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| rules | {r['counts'].get('APPROVED',0)} | {r['counts'].get('REVIEW',0)} | {r['counts'].get('REJECTED',0)} | {r['tp']} | {r['fp']} | {r['fn']} | {r['tn']} | {r['precision_default']:.3f} | {r['recall_default']:.3f} | {r['approval_rate']:.3f} | {r['default_rate_among_approved']:.3f} |
| hybrid | {h['counts'].get('APPROVED',0)} | {h['counts'].get('REVIEW',0)} | {h['counts'].get('REJECTED',0)} | {h['tp']} | {h['fp']} | {h['fn']} | {h['tn']} | {h['precision_default']:.3f} | {h['recall_default']:.3f} | {h['approval_rate']:.3f} | {h['default_rate_among_approved']:.3f} |

Decisions that changed between the modes: **{result['decisionChanged']}** of {len(rows)}.
Rows rejected purely because the amount exceeded capacity (identical in both modes): {result['rejectedByAmountOnly']}.

## Reading the result

- The affordability check gates both modes the same way; the AI can only move applications whose
  rule score sits near the approve/review thresholds. That is why most decisions are unchanged.
- The AI model was trained on labels from a latent-risk generator that shares inputs with the rules
  but not their weights; its stand-alone AUC shows how much of that generator it recovers.
- Improvement is only claimed where the table shows it. If hybrid AUC or default-rate-among-approved
  is not better than rules, the honest conclusion is that on this synthetic data the 20 % AI weight
  adds little at the decision level.

## Limitations

- Labels are synthetic and produced by a hand-written generator; nothing here measures real credit risk.
- One partner cap was assumed for all rows; the demo stores have different caps.
- One model, one seed, one split; no hyper-parameter search, calibration or confidence intervals.
- The generator and the rules were written by the same author, so shared assumptions inflate both.

## Decision examples where the modes disagree

| customer | amount | default | rules (score) | hybrid (score) | AI risk | hybrid reasons |
| --- | ---: | ---: | --- | --- | ---: | --- |
"""
    for e in examples:
        md += f"| {e['customerId']} | {e['requestedAmount']} | {e['default']} | {e['rules']} ({e['rulesScore']:.3f}) | {e['hybrid']} ({e['hybridScore']:.3f}) | {e['aiRisk']:.3f} | {', '.join(e['reasonsHybrid'])} |\n"
    (out / "evaluation.md").write_text(md)
    print(json.dumps({k: v for k, v in result.items() if k != "examples"}, indent=2))


if __name__ == "__main__":
    main()
