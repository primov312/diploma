Rocket Credit — Credit Analysis (Python)

Goal: Make an approve/deny decision for a checkout using partner/rocket history and amount, return a fast response to the Gateway, and persist a fully explainable audit trail (reasons + factor contributions).
Hot-path weights (current): Rocket 50%, Partner 30%, Amount 20%. (Credit Bureau & Social are scaffolded but disabled.)

⸻

How it works (end-to-end)
	1.	Gateway calls POST /creditscore with {"userId": ..., "cartTotal": ...}.
	2.	app/main.py validates the payload and delegates to app/scoring.py.
	3.	scoring fetches a feature bundle from User-Data (HTTP), runs:
	•	score_partner(...), score_rocket(...), score_amount(...)
	•	Combines subscores using a dynamic policy from DB (app/policy.py).
	4.	Eligibility & affordability checks applied (KYC, and cartTotal ≤ credit_limit).
	5.	Decision produced: {approved, score:int(0..100), reason}.
	6.	Audit is built (reasons array, factor scores, applied weights, thresholds, affordability flag).
	7.	DB write (app/db.py): insert decision row into credit_requests (JSONB reasons & factors).
	8.	Event (app/events.py, optional): publish credit.evaluated for notifications/analytics.
	9.	Logging (app/logging.py): structured logs with trace ids and key fields.

Startup path:
	•	docker/entrypoint.sh waits for Postgres, runs alembic upgrade head, then starts Uvicorn.

⸻

Repository layout & responsibilities

rocket-credit-analysis/
├─ app/
│  ├─ db.py           # DB engine helpers & inserts of decision audit into credit_requests.
│  ├─ events.py       # Kafka producer (optional): publish credit.evaluated after decision.
│  ├─ features.py     # (Reserved) Shared transforms if/when you promote a feature store.
│  ├─ logging.py      # Structured logging / OTEL hooks (kept minimal; extend as needed).
│  ├─ main.py         # FastAPI app; /creditscore endpoint; wires scoring + persistence.
│  ├─ models.py       # Pydantic models: CreditRequest, CreditResponse, (and ModelMeta if used).
│  ├─ policy.py       # Loads versioned weights/thresholds from DB with small in-proc cache.
│  ├─ reasons.py      # Canonical reason codes; stable strings used in scoring & audits.
│  ├─ scoring.py      # Core scoring logic, eligibility, factor subscores & audit bundle.
│  └─ settings.py     # Env-driven config (timeouts, URLs, thresholds fallback, cache TTL, etc.).
│
├─ docker/
│  └─ entrypoint.sh   # Wait for DB → run Alembic → start Uvicorn.
│
├─ migrations/
│  ├─ versions/
│  │  ├─ c9f16d8fe85c_v1_initial_schema.py          # V1: baseline tables (credit_requests, etc.).
│  │  ├─ ac16af2af24d_v2_add_new_checks.py          # V2: extra JSONB/check columns & indexes.
│  │  ├─ a97f2cec16c7_v3_add_user_external_id.py    # V3: add user_external_id BIGINT + index.
│  │  ├─ 51e7e2b658cb_v4_add_reasons_and_factors.py # V4: JSONB columns reasons, factors + GIN.
│  │  └─ d6ab4918d175_v5_credit_policy.py           # V5: credit_policy_versions (versioned policy).
│  ├─ env.py                # Alembic env: reads SQLALCHEMY_URL from env.
│  ├─ README                # Alembic scaffold notes.
│  └─ script.py.mako        # Alembic template.
│
├─ tests/                   # Unit/integration/contract tests (add k6/Gatling separately).
├─ alembic.ini              # Alembic config; URL injected by env.py.
├─ Dockerfile               # Copies app/, migrations/, alembic.ini; uses entrypoint.sh.
└─ requirements.txt         # Runtime deps (FastAPI, httpx, SQLAlchemy, Alembic, etc.).


⸻

Domain models (API)
	•	CreditRequest
Fields:
	•	userId: int
	•	cartTotal: float
	•	CreditResponse
Fields:
	•	approved: bool
	•	score: int (0–100)
	•	reason: str ("OK", "REVIEW", or top reason code like "AFFORDABILITY_LIMIT_EXCEEDED")

The Gateway contract remains unchanged: POST /creditscore returns the shape above.

⸻

Scoring policy (current)
	•	Active factors:
	•	Rocket (50%): on-time ratio, tenure, recent 30+ DPD, number of active plans.
	•	Partner (30%): order count (12m), AOV, on-time ratio, tenure, refund rate.
	•	Amount (20%): basket utilization relative to capacity (max(credit_limit, income*mult)).
	•	Disabled (scaffolded for future):
	•	Credit Bureau (15%), Social (15%) — commented in scoring.py; can be re-enabled later and added to policy.py.
	•	Eligibility gates:
	•	KYC must pass; social consent only matters when Social is enabled.
	•	Affordability (hard guard): cartTotal ≤ credit_limit → otherwise add reason AFFORDABILITY_LIMIT_EXCEEDED.
	•	Thresholds: approve if score ≥ approve_threshold (default 0.60); optional review band if ≥ review_threshold (default 0.50).
	•	Explainability:
	•	Reasons (array): human-stable codes from app/reasons.py.
	•	Factors (JSON): per-factor subscore + applied weight from the live policy.
	•	Both are saved on each decision.

⸻

Dynamic policy (DB-driven)
	•	Table: credit_policy_versions (V5)
Columns: policy_name, version, weights (JSONB), thresholds (JSONB), flags (JSONB), valid_from, enabled, metadata.
	•	Loader: app/policy.py#get_current_policy()
	•	Picks the latest enabled version effective at valid_from ≤ now().
	•	Renormalizes weights to sum to 1.
	•	Clamps thresholds to safe ranges.
	•	Caches in-process for policy_cache_ttl_seconds (default 30s).
	•	Update without deploy: insert a new row with a higher version or later valid_from.
	•	The service refreshes within the cache TTL.
	•	Past decisions remain explainable because applied weights & thresholds are stored with each decision.

⸻

Database schema (high level)
	•	credit_requests (created in V1, extended in V2–V4)
	•	Core fields: id, user_external_id (V3), request_amount, score, decision, timestamps…
	•	reasons JSONB (V4): array of reason codes (GIN-indexed).
	•	factors JSONB (V4): per-factor subscore & weight applied.
	•	Indexes for analytics (e.g., decision, reasons GIN).
	•	credit_policy_versions (V5)
	•	Versioned configuration for weights/thresholds/flags.

Alembic is run on container boot (see entrypoint) so tables are always up-to-date.

⸻

Request lifecycle (detailed)
	1.	HTTP → POST /creditscore (app/main.py)
	•	Parses CreditRequest, injects Settings.
	2.	Scoring (app/scoring.py)
	•	Fetch features from User-Data: GET {USER_DATA_URL}/... (path configurable).
	•	Eligibility: KYC, (optional social consent).
	•	Subscores: score_partner, score_rocket, score_amount → each returns (0..1, reasons[]).
	•	Policy: get_current_policy() → weights & thresholds.
	•	Aggregate: weighted sum → score_float ∈ [0,1]; map to score_int = floor(100*score_float).
	•	Affordability: ensure cartTotal ≤ credit_limit.
	•	Decision: approved, final_reason, build DecisionAudit.
	3.	Persist (app/db.py)
	•	Insert into credit_requests: score, decision, reasons (JSONB), factors (JSONB), user_external_id, request_amount.
	4.	Event (optional, app/events.py)
	•	Publish credit.evaluated with (approved, score, reasons, factors, modelVersion/policyVersion, occurredAt).
	5.	Respond
	•	Return CreditResponse to the Gateway immediately; persistence/events never block the response (best effort).

⸻

Configuration (env vars)

Common settings (see app/settings.py):
	•	SQLALCHEMY_URL — e.g., postgresql+psycopg://postgres:postgres@creditdb:5432/creditdb
	•	USER_DATA_URL — base URL of User-Data service (HTTP)
	•	USER_DATA_TIMEOUT — seconds (default ~1.5)
	•	APPROVE_THRESHOLD, REVIEW_THRESHOLD — fallback thresholds if policy missing
	•	POLICY_CACHE_TTL_SECONDS — policy cache TTL (default 30s)
	•	INCOME_AFFORDABILITY_MULTIPLIER — default 0.3 for amount capacity
	•	(Kafka) KAFKA_BOOTSTRAP, topic names — if events enabled

All config is env-driven so the same image runs everywhere.

⸻

Deployment
	•	Dockerfile copies: app/, migrations/, alembic.ini, docker/entrypoint.sh.
	•	EntryPoint: waits for DB, runs alembic upgrade head, then starts Uvicorn.
	•	Compose/K8s: ensure the DB service is healthy before starting this container (depends_on/healthcheck or initContainers).

⸻

Observability & reliability
	•	Logging: JSON logs with decision summary (approved, score, reason), policy version, latencies.
	•	Timeouts: Tight httpx timeouts for User-Data.
	•	Fail-safes: If policy table unavailable, falls back to Settings thresholds/weights.
	•	Idempotency: Use upstream analysisId/trace id if/when added; the current API is pure & stateless.
	•	Metrics (add via OTEL): request latency, approval rate, bad rate by vintage/partner, reason distribution, policy version adoption.

⸻

Extending the model
	•	Re-enable Bureau & Social: uncomment blocks in scoring.py, add weights in policy.py/DB; update reason codes.
	•	ML swap: replace a subscore function with an ML model returning 0..1 and a few reason codes (via SHAP→reason mapping).
	•	New factors: add a scorer function, register a weight in policy, and persist its contribution in factors.

⸻

Local dev quickstart

# 1) bring up DB & app
docker compose up -d creditdb
docker compose up --build credit-analysis

# 2) smoke request
curl -s http://localhost:8082/creditscore \
  -H 'Content-Type: application/json' \
  -d '{"userId":123, "cartTotal":250.0}'

# 3) check tables
docker exec -it creditdb psql -U postgres -d creditdb -c '\dt'
docker exec -it creditdb psql -U postgres -d creditdb \
  -c 'select decision, score, reasons from credit_requests order by created_at desc limit 5;'


⸻

Security & compliance notes
	•	PII minimization: scorer only handles numeric features and flags; raw PII stays in User-Data.
	•	Consent logging: Social features must be behind a feature flag + explicit consent.
	•	Auditability: every decision stores reasons, applied weights, thresholds, and affordability flag; policy is versioned.

⸻

Owner: Credit Platform (Rocket)
Change control: Policy changes via credit_policy_versions; code changes via PR + CI/CD.
SLOs: /creditscore p95 ≤ 40 ms (excluding upstream User-Data), error rate ≤ 0.1%.