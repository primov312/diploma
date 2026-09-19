# Rocket Credit — Simple Diploma Completion Plan

Updated: 2026-09-19. This plan replaces the earlier 12-phase distributed-system roadmap.

Scope: [README](../README.md). Design: [simple architecture](DIPLOMA_ARCHITECTURE.md). Research description: [diploma topic](DIPLOMA_PROJECT_TOPIC.md).

## 1. Goal and completion boundary

Build a customer account with demo partner transaction history, a credit request form, explained decisions and three store pages linking into the application. Demonstrate authentication, multithreading, scoring modules, safe data keeping and AI analysis.

Target runtime: one React app, one Java backend, one Python analysis service and one PostgreSQL database. No payments, separate partner backend, identity-provider deployment, queue, object store or distributed workflow is required.

The code has not yet been simplified. Existing microservices and earlier Docker smoke results are reuse material, not evidence that this target is implemented. All steps below are pending. This documentation change starts no services.

## 2. What to reuse and what to stop building

- Reuse the React layout/pages, useful User Data entities and seed concepts, Gateway's basic request coordination, and Python rule functions.
- Build `rocket-credit-backend` from the User Data Java/Spring foundation, bringing only required orchestration across. Account for the old Gateway's Java 17 / Spring Boot 2 / `javax` code versus User Data's Java 21 / Spring Boot 3 / `jakarta` code; adapt source rather than copying both application bootstraps.
- Turn `rocket-credit-analysis` into a stateless scoring service. Backend stores its result in the single database.
- Keep Payment, Repayment, old Gateway/User Data deployments and claim-check code out of the new runtime. Retain them as reference until the simplified app works; do not delete user changes or volumes.
- Drop the old roadmap's offers, payment acceptance, operator portal, partner credentials/webhooks, Keycloak, bank integration, scraping, outbox and recovery-worker requirements.
- Supersede old persona amounts and payment-oriented scenarios when writing the new demo script. The old `DEMO_SCENARIOS.md` is a historical fixture description until updated in Step 3.

## 3. Implementation order

| Step | Depends on | Deliverable |
| --- | --- | --- |
| 1. Reduce the runtime | Existing repository | Java backend + Python analysis + one DB start together. |
| 2. Add accounts and sessions | Step 1 | Register, login, logout and user isolation. |
| 3. Add demo data and history | Step 2 | Three partners and per-user transaction history. |
| 4. Implement rules and saved decisions | Step 3 | Complete request → score → save → read flow. |
| 5. Connect the customer/store UI | Step 4 | Working browser demonstration with rules. |
| 6. Add trained AI analysis | Steps 3–4 | Evaluated model and optional hybrid scoring. |
| 7. Demonstrate multithreading | Steps 4 and 6 | Sequential/parallel comparison using the same inputs. |
| 8. Verify and write the diploma | Steps 1–7 | Focused tests, experiments and reproducible demo. |

Security checks and tests accompany each step. Do not postpone ownership checks until the final step.

### Step 1 — Reduce the runtime

Locations: proposed `rocket-credit-backend/`, existing `rocket-credit-analysis/`, `rocket-credit-deployment/`.

- [x] 1.1 Record the current state and preserve existing source/volumes. Select the User Data Java foundation for the consolidated backend; copy source only and remove unused startup/configuration dependencies.
- [x] 1.2 Create backend packages for auth, users, partners, transactions, applications and analysis. Replace required cross-service user-data calls with local service/repository calls.
- [x] 1.3 Make Python accept a validated JSON feature bundle and return a decision. Remove its runtime dependency on PostgreSQL, User Data HTTP, MinIO and Kafka. Replace DB policy loading with a versioned local configuration; retain useful pure rule functions.
- [x] 1.4 Add `rocket-credit-deployment/docker-compose.diploma.yml` for backend, analysis and one PostgreSQL database with a new named volume. Serve built React files from Java; support Vite's API proxy for development. Give analysis only an internal port and simple backend-call authentication via an environment secret.
- [x] 1.5 Document one start command, one stop command and health endpoints. Label original Compose as legacy. Keep old databases untouched; new synthetic fixtures need no production-style cross-database migration.

Done when: all three server containers start without Redis, MinIO or the old services; backend can call the stateless analysis health/scoring endpoints.

Status 2026-09-19: done and verified with `docker-compose.diploma.yml` (see `rocket-credit-deployment/DIPLOMA_RUNTIME.md`). The V1 migration already creates all six tables from the architecture document, so 3.1 is reduced to reviewing constraints. The Python rule modules (`app/rules/`) already follow the 4.3/4.4 split; Step 4 refines and tests them against fixtures.

### Step 2 — Implement accounts and session authentication

Locations: backend `auth`/`users`, migrations and frontend auth integration.

- [x] 2.1 Add users with unique normalized email and password hash. Use Spring Security's password encoder, request validation and generic login errors. Never return hashes in API responses.
- [x] 2.2 Implement registration, login, current-user lookup and logout using framework sessions. Rotate session IDs on login; invalidate on logout. Document that restart ends in-memory sessions.
- [x] 2.3 Configure HttpOnly cookies, SameSite, Secure when using HTTPS, CSRF handling and same-origin frontend/API requests. Include the CSRF token flow for the React client.
- [x] 2.4 Derive user identity from the session for every private query. Test two accounts, wrong passwords, absent/expired sessions, missing CSRF tokens and cross-user record IDs.

Done when: register → login → protected request → logout works, and customer A cannot access customer B's data. Capture this sequence for the authentication chapter.

Status 2026-09-19: done. Backend `auth` package + `AuthFlowTest`/`OwnershipTest` (Testcontainers), React `src/api/client.ts` (CSRF cookie flow), `AuthContext`, `RequireAuth`, `/login`, `/register`. API summary in `rocket-credit-backend/README.md`. `GET /api/transactions[/{id}]` exists already (ownership-scoped) so the cross-user check is a real HTTP test; 3.3 adds `GET /api/me` (done) and partner filtering (done) on top of seeded data.

### Step 3 — Seed partner history and demonstrate safe storage

Locations: backend migrations, fixture loader, history APIs and `research/fixtures/`.

- [x] 3.1 Add partners, products, transactions, synthetic financial profiles and credit-application tables from the architecture document. Use foreign keys, decimal money, timestamps and database constraints; support USD only.
- [x] 3.2 Seed StreamBox, MarketHub and Threadly catalogs plus several customers with distinct histories. Make repeated seeding safe through stable fixture IDs. New registrations get a clearly labeled synthetic starter dataset, not another account's history.
- [x] 3.3 Add public catalog/partner reads and authenticated `GET /api/me` and `GET /api/transactions` with partner filtering. Financial fixture values cannot be arbitrarily overwritten by the browser.
- [x] 3.4 Use parameterized ORM queries, restricted DB credentials and environment secrets. Redact passwords, cookies and personal payloads from logs. Document local disk protection separately from password hashing.
- [x] 3.5 Demonstrate one local DB backup/restore, and update `DEMO_SCENARIOS.md` with new fixture identities and expected behavior. Keep old fixture expectations clearly separated until the new scoring policy is applied.

Done when: two customers see their own three-partner history; duplicate seeding does not inflate it; the database stores hashes and restores successfully.

Status 2026-09-19: done. Catalog in `V2__seed_partners_and_products.sql`; personas in `fixtures/demo-customers.json` loaded by `DemoDataLoader` (restart: 0 inserted); `StarterDataService` for new registrations; `GET /api/partners[/{slug}]`, `GET /api/me/profile`; `DemoDataTest`. Backup/restore scripts in `rocket-credit-deployment/diploma/` demonstrated. Security notes and limits in `docs/SAFE_DATA_KEEPING.md`; scenarios in `docs/DEMO_SCENARIOS.md`.

### Step 4 — Implement modular scoring and persist decisions

Locations: Python `app/` modules, backend `applications`/`analysis`, API models.

- [x] 4.1 Define one request: partner ID, requested amount, optional product ID and `useAi`. Use session identity. Validate positive amount/currency; resolve product ownership and price server-side when product ID is present.
- [x] 4.2 Prepare profile, partner-history and finance features from stored synthetic data. Keep all preparation sequential initially; capture the observation time and send derived data without identifying fields to Python.
- [x] 4.3 Separate profile rules, history rules, affordability and decision combination into testable Python functions. Correct existing zero-capacity behavior and return an explicit `decisionStatus` rather than mapping every negative result to generic denial.
- [x] 4.4 Return decision, score, possible amount, reasons, factors and policy version. Use the architecture's simple policy; missing required evidence gives review, insufficient capacity gives rejection. A lower possible amount is a suggestion for a new request, not an accepted purchase.
- [x] 4.5 Implement `POST /api/applications`, `GET /api/applications` and `GET /api/applications/{id}`. Save request, feature snapshot, result, AI choice/status and versions atomically in one DB; enforce ownership on reads.
- [x] 4.6 Test approval, rejection, review, over-cap amount, zero capacity and invalid inputs. Return a technical error on analysis timeout or failed DB write; do not invent or display a saved decision. Disable repeated submit while pending; duplicate financial execution is irrelevant because this app makes no payment.

Done when: an API request produces an explained, persisted decision that survives application restart and can only be read by its owner.

Status 2026-09-19: done. `applications/` (service, controller, `features/` providers + sequential preparation), Python `app/rules/*` with `tests/test_scenarios.py` pinning the six demo outcomes (policy `rules-v1`, review band 0.40–0.65). Verified live: all six scenarios, `useAi` → `UNAVAILABLE`, restart keeps rows, analysis stopped → 503 and no row. UI-side "disable submit while pending" belongs to Step 5.

### Step 5 — Connect the customer app and demo store pages

Location: `demo-repository/src/`.

- [x] 5.1 Add login/register, dashboard, transaction history, request form, decision detail and application-history routes. Connect them through one API client with session cookies and CSRF support.
- [x] 5.2 Replace hard-coded account figures with stored data. Clearly distinguish historical purchases from credit applications.
- [x] 5.3 Build three branded store pages with small catalogs and “Apply with Rocket Credit” links. Share components and use backend catalog data; no store backend or checkout session service is needed.
- [x] 5.4 Allow direct selection of partner/amount and store-prefilled requests. Preserve intended navigation through login and resolve product details from the backend.
- [x] 5.5 Show approved, rejected and review outcomes with plain-language reasons and suggested possible amount. State that review is an inconclusive automatic result, and no money is moved. Support loading, errors, empty data, logout and mobile/keyboard use.

Done when: an examiner can register, inspect history and submit a request from Rocket Credit or any of the three store pages.

Status 2026-09-19: implemented (`demo-repository/src/pages/app/*`, `pages/stores/*`, `api/*`, `utils/reasons.ts`); routes `/stores`, `/stores/:slug`, `/apply`, `/history`, `/applications`, `/applications/:id`; store links carry only partner slug + product id. Verified by type-check, production build and the served bundle; the browser click-through is part of 8.2.

### Step 6 — Add AI analysis and evaluate it

Locations: Python analysis and `research/training/`, `research/results/`.

- [ ] 6.1 Specify the model's target and inputs: normalized history, profile and synthetic financial features. Generate reproducible labeled data with fixed seeds; document the label-generation assumptions.
- [ ] 6.2 Split by synthetic customer before preprocessing/training. Exclude future transactions, customer identifiers, target labels and fields that directly reveal the target from model inputs.
- [ ] 6.3 Train one logistic-regression model and compare it with the rules baseline. Save trusted model/preprocessing artifacts, feature schema and version locally; load once at startup. No model registry or separate AI service is needed.
- [ ] 6.4 Add the AI module to the same Python scoring process. Return risk semantics, version and understandable feature contributions; combine it with rules using documented weights.
- [ ] 6.5 Wire “Use AI analysis for this request” into the form and saved result. When disabled, do not invoke inference. If the model alone is unavailable, record fallback and use rules; if the whole analysis service fails, use the technical-error behavior from Step 4.
- [ ] 6.6 Evaluate both modes on identical held-out data and save a confusion matrix, precision/recall, ROC-AUC where meaningful and decision examples. Report improvement only if measured; explain synthetic-data limitations.

Done when: training/evaluation is reproducible, the UI can use rules or hybrid analysis, and saved results identify the exact model/policy and whether AI ran.

### Step 7 — Add the multithreading experiment

Locations: backend feature-preparation code and `research/benchmarks/`.

- [ ] 7.1 Extract profile, history and finance preparation behind three independent provider interfaces. Keep a sequential implementation for comparison.
- [ ] 7.2 Add one shared bounded Java executor and a parallel implementation. Give each worker its own short DB read transaction or immutable materialized inputs. Do not share JPA sessions, lazy entities or mutable score state.
- [ ] 7.3 Join all required tasks before the single scoring call. Add a deadline, bounded queue, explicit task-failure handling and clean executor shutdown.
- [ ] 7.4 Assert identical features and decisions for both modes. Exercise simultaneous requests from two users to catch accidental shared state.
- [ ] 7.5 Benchmark repeated runs on normal local data, then with controlled simulated I/O delays. Record pool size, timings, median/p95, errors and dataset size in CSV. Separate simulated-delay results from ordinary operation; discuss thread overhead and possible lack of speedup.

Done when: the diploma contains an understandable sequential/parallel experiment with measured results and a correctness check, without requiring more services.

### Step 8 — Verify, document and present

Locations: tests, `research/`, `docs/` and the run instructions.

- [ ] 8.1 Run focused checks: authentication/ownership/CSRF, feature calculations, score/cap boundaries, decision persistence, rules/AI behavior and thread-result equality.
- [ ] 8.2 Add one browser smoke scenario covering login, history, a store handoff, application submission, saved result and logout. Include approval, rejection and review fixture cases in API tests.
- [ ] 8.3 Finalize evidence for all five research topics using the table below. Include a DB backup/restore example and clearly state local security limits.
- [ ] 8.4 Write the diploma chapters and a short demo script. Update screenshots, architecture and fixture instructions to match implemented behavior.
- [ ] 8.5 Verify a fresh start from the simplified Compose setup and document shutdown. Archive or remove unused legacy code only in a separate reviewed cleanup after the new path passes; deleting old volumes is not part of completion.

Done when: another person can start the simple app, follow the demo and reproduce the two research experiments without running the legacy stack.

## 4. Minimum research evidence

| Topic | Implementation evidence | Experiment / check |
| --- | --- | --- |
| Authentication | Password hashes, Spring Security session, cookie and logout flow | Wrong-password, no-session and cross-user access tests |
| Multithreading | Shared bounded executor and three independent feature providers | Same results; sequential/parallel timing table |
| Scoring modules | Profile/history rules, affordability and combiner | Known inputs, expected factors, boundary decisions |
| Safe data keeping | Ownership, constraints, secret configuration and backup procedure | Denied reads, no secret logging, backup/restore |
| AI analysis | Offline training, versioned model, inference and fallback | Rules vs hybrid results on fixed held-out customers |

## 5. First implementation task

Start with Step 1: create the consolidated backend and database-free Python analysis path, then run them against the new diploma database. Do not add features to the legacy five-service checkout while building the simplified route.

After that, prioritize a thin complete path: login → own transaction history → rules-only request → saved result. Add the trained AI model and thread experiment to that working path.
