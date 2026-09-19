# rocket-credit-analysis

Stateless scoring service for the diploma runtime. It receives a JSON feature
bundle from the Java backend and returns an explained decision. It has no
database, object store, broker or callback into the backend.

```
GET  /health            -> {"status":"ok","policyVersion":"rules-v1",...}   (no auth)
GET  /policy            -> active policy                                     (X-Analysis-Token)
POST /score             -> ScoreResponse                                     (X-Analysis-Token)
```

Request/response models: [`app/models.py`](app/models.py). Policy:
[`app/policy.json`](app/policy.json) (versioned; loaded once at startup).
Rule modules: [`app/rules/`](app/rules/) — `profile`, `history`,
`affordability`, `combiner`. Each is a pure function of (features, policy).
AI: [`app/ai.py`](app/ai.py) loads the trusted artifact [`app/model/model-v1.json`](app/model/model-v1.json)
(logistic regression trained offline in `research/training/`) once at startup; with `useAi: true` its risk
estimate enters the score as `0.8 * rules + 0.2 * (1 - risk)` and per-feature contributions are returned.
Without the flag the model is never invoked; without the artifact (or on inference failure) the result is
rules-only with `aiStatus: UNAVAILABLE`.

## Run locally

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
pytest -q
ANALYSIS_SHARED_SECRET=dev-secret uvicorn app.main:app --port 8000 --reload
```

## Legacy

The previous checkout-oriented service (`/creditscore`, PostgreSQL audit table, Kafka events, MinIO
claim-check) was removed from the repository after Step 7; see git history up to `1b886c0`.
