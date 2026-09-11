rocket-credit-user-data — Context & Architecture

Service role: The User Data Service (UDS) is the system of record for customer profiles and their local purchase footprint inside CreDIT. It aggregates and normalizes user information coming from partner platforms and sanctioned external sources (email-domain/identity checks, social OAuth when consented), persists the minimum necessary data, and exposes a clean API for the rest of the platform (Gateway, Credit Analysis, Payment, Repayment).

⸻

1) Runtime, Config, and Ports
	•	Process port: 8080 (internal), commonly mapped to 8081 on the host in Compose.
	•	Profile: dev by default; override via SPRING_PROFILES_ACTIVE.
	•	Database: PostgreSQL (jdbc:postgresql://userdb:5432/userdb), credentials from env.
	•	Schema management: Flyway reads src/main/resources/db/migration (V1..V4).
	•	JPA policy: ddl-auto=none in prod (Flyway owns schema).
	•	External API configs: external.apis.* block for VK/Meta OAuth, and email-verifier (dev values are safe defaults).
	•	Logging: Service and Flyway DEBUG in dev; adjust per environment.

Security heads‑up: keep all client secrets and verifier API keys in a secret store (Vault/Parameter Store) and pass them to the service as env vars; never commit real credentials.

⸻

2) Directory Layout & What Lives Where

rocket-credit-user-data
├─ .openapi-generator/                 # Keeps the generator state and templates (if any tweaks)
├─ src/main/java/com/rocketcredit/userdata
│  ├─ UserDataApplication.java         # Spring Boot entry point
│  ├─ config/
│  │  └─ SecurityConfig.java          # Spring Security config (authn/z, CORS, stateless sessions)
│  ├─ api/
│  │  ├─ ApiUtil.java                  # OpenAPI helper for examples and content negotiation
│  │  ├─ UsersApi.java                 # **Contract** (generated OpenAPI interface), declares endpoints
│  │  ├─ UserApiController.java        # **Implementation** of UsersApi; maps to repos, DTOs
│  │  └─ OAuthCallbackController.java  # Social OAuth callback/linking handler (VK/Meta) on user consent
│  ├─ entity/
│  │  ├─ UserEntity.java               # JPA entity for a user profile
│  │  ├─ TransactionEntity.java        # JPA entity for a purchase/BNPL-related transaction
│  │  ├─ PaymentMethodEntity.java      # JPA entity for user’s saved payment methods (tokenized)
│  │  └─ SocialAuthEntity.java         # JPA entity for storing OAuth link state & minimal claims
│  ├─ model/
│  │  ├─ User.java / NewUser.java      # API DTOs for read/create
│  │  ├─ Transaction.java              # API DTO for transaction view
│  │  ├─ PaymentMethod.java            # API DTO for payment methods
│  │  ├─ CreditProfile.java            # Aggregated view used by Credit Analysis
│  │  └─ SocialAuthRequest.java,
│  │     SocialInsights.java           # Consent request + summarized external signals
│  └─ repo/
│     ├─ UserRepository.java           # Spring Data JPA repositories
│     ├─ TransactionRepository.java
│     ├─ PaymentMethodRepository.java
│     └─ SocialAuthRepository.java
│
├─ src/main/resources
│  ├─ application.yml                  # Ports, datasource, Flyway, logging, and external API endpoints
│  └─ db/migration/
│     ├─ V1__initial_schema.sql        # Base tables (users, transactions, payment_methods, social_auth)
│     ├─ V2__seed_users.sql            # Dev seeds for smoke testing
│     ├─ V3__add_social_fields.sql     # Fields to support OAuth and social insights
│     └─ V4__add_new_checks.sql        # Additional columns/indices used by newer checks
│
├─ Dockerfile                          # Container image for this service
├─ pom.xml                             # Build deps and plugins (Spring Boot, JPA, Flyway, OpenAPI)
├─ README.md                           # Quick start and API notes
└─ .gitignore / .openapi-generator-ignore


⸻

3) API Surface (OpenAPI‑driven)

All routes are defined by UsersApi and implemented in UserApiController. The key resources:

Users
	•	GET /users/{id} → User
	•	POST /users with NewUser → creates and returns User
	•	PUT /users/{id} with User → updates core attributes
	•	DELETE /users/{id} → tombstones the user

Financial Footprint
	•	GET /users/{id}/transactions → Transaction[] (normalized transactions from partner + local BNPL)
	•	GET /users/{id}/payment-methods → PaymentMethod[] (tokenized references only; no PAN storage)

Credit Aggregation View
	•	GET /users/{id}/credit-profile → CreditProfile
	•	Combines: on‑platform spend summary, transaction count, average ticket, on‑time payment rate, number of payment methods, BNPL activity in last N months, and (if consented) SocialInsights derived from verified work email/org and sanctioned social profile claims.

Social Linking / Consent (optional)
	•	GET /oauth/{provider}/callback (e.g., VK/Meta) → handled by OAuthCallbackController, which
	•	verifies state, exchanges code for token using provider client id/secret,
	•	extracts minimal claims (e.g., profile id, employment org) and stores them in SocialAuthEntity,
	•	updates UserEntity flags (e.g., socialConsent=true) and attaches provider handle(s).

Privacy by default: All social/identity checks are opt‑in and gated by explicit user consent. The service is fully functional without them.

⸻

4) Data Model (High level)
	•	UserEntity: id (PK), name, email, socialConsent flag, creditBureauScore (if available), annualIncome (nullable), socialHandles (JSON), created/updated timestamps.
	•	TransactionEntity: id, user_id (FK), amount, currency, date, partner_order_id, status, channel, created_at.
	•	PaymentMethodEntity: id, user_id (FK), type (e.g., card/bank), last4, brand, token_ref, created_at.
	•	SocialAuthEntity: id, user_id (FK), provider (vk/meta), provider_user_id, access_token_ref (vault key, not raw token), scopes, linked_at.

Schema lifecycle: V1 creates the base tables; V3 introduces fields to support OAuth/social insights; V4 adds columns/indices for new checks (e.g., email‑domain verification flags, partner risk markers). Flyway gives deterministic, versioned evolution.

⸻

5) Service Responsibilities & Flows
	1.	Normalize & persist user data on first touch (from partner token or gateway‑supplied payload). Subsequent calls update the record if fields changed.
	2.	Expose a stable JSON contract that other services can rely on, hiding storage details and partner‑specific messiness.
	3.	Assemble CreditProfile for the Credit Analysis Service (CAS) on demand:
	•	pull local aggregates (transactions/payment methods),
	•	enrich with verified email‑domain signals and optional social consent claims,
	•	return a compact structure with just the attributes CAS needs.

Checkout orchestration (happy path): Gateway resolves user → calls UDS → passes returned userId to CAS → CAS fetches credit-profile from UDS when needed → downstream Payment/Repayment proceed. This keeps services loosely coupled and identifiers internal.

⸻

6) Security & Privacy Posture (essentials)
	•	Authentication/Authorization: Spring Security in stateless mode; prefer JWT from Gateway with service‑to‑service trust (mTLS in prod). Public endpoints should be restricted; admin/seed/debug endpoints disabled in prod.
	•	PII minimization: Store the bare minimum. Encrypt at rest (Postgres TDE/volume encryption) and in transit (TLS everywhere). Consider field‑level encryption for sensitive columns (email).
	•	Secrets: Only via env/secret store; no secrets in repo. Rotate OAuth client credentials. Never store raw social access tokens—store references.
	•	Consent & Compliance: Track explicit consent for each external source. Deny social lookups when socialConsent=false. Provide “forget me” routines aligned with GDPR/CCPA.
	•	Audit: Log data‑access events and auth decisions (PII‑safe). Add DB audit triggers for create/update/delete on user‑owned tables.
	•	Abuse controls: Rate‑limit the write endpoints, validate and sanitize all inputs, and apply allow‑lists for email‑domain verification calls.

⸻

7) Local Development
	•	Bring up Postgres (userdb) and the service with Docker Compose. userdb is published on 5433 for host troubleshooting; the service talks to it on 5432 inside the network.
	•	Flyway runs on startup; in dev the seed migration populates a few users and sample transactions.
	•	Use the OpenAPI UI (if enabled) to hit /users/{id}, /users/{id}/credit-profile, and related endpoints.

Smoke tests to try
	1.	Create a user → fetch it → update it → fetch credit-profile to see aggregates.
	2.	Add transactions and payment methods (via seed or dedicated admin fixtures) → confirm aggregates update.
	3.	Simulate social consent (dev callback) → confirm socialConsent=true and insights surface in credit-profile.

⸻

8) Observability
	•	Logging: Request/response summaries for controller methods; scrub PII before logging. Use structured logs (JSON) for ingestion.
	•	Metrics: Add timer counters around repo calls and external API calls; emit percentiles for DB latency and cache hit ratios.
	•	Tracing: Propagate traceparent from Gateway; annotate controller spans with userId (as a tag only, not in message bodies).

⸻

9) Extension Points & Next Steps
	•	Harden the OAuth link flow (PKCE, per‑provider scopes) and the email‑domain verifier adapter.
	•	Add idempotency keys to write endpoints.
	•	Introduce caching for credit-profile with short TTL to shield CAS during peak loads.
	•	Backfill read‑only projections (materialized views) for common aggregates.
	•	Write Testcontainers‑based integration tests that boot Postgres + run Flyway migrations automatically.

Bottom line: UDS owns user truth, returns compact, privacy‑respecting JSON to the rest of the platform, and evolves its schema predictably via Flyway. Keep the core lean, consent‑aware, and observable from day one.