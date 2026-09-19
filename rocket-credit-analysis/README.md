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

## Run locally

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
pytest -q
ANALYSIS_SHARED_SECRET=dev-secret uvicorn app.main:app --port 8000 --reload
```

## Legacy

The previous checkout-oriented service (`/creditscore`, PostgreSQL audit
table, Kafka events, MinIO claim-check) is kept as reference in
[`legacy/`](legacy/). It is not part of the diploma runtime and is not built
into the image.
