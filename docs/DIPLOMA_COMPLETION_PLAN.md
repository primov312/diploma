# Rocket Credit — Diploma Completion Plan

## Goal

Deliver a working Buy Now, Pay Later prototype in which a customer signs in, shops at a demo partner, requests financing through Rocket Credit, receives an explainable decision, and can later view transactions and repayment plans in a customer dashboard.

The diploma focus is not just the visual demo. It must demonstrate authentication, safe data handling, concurrent service orchestration, explainable scoring, and a consent-first AI-assisted factor.

## Current-State Assessment

| Area | Status | Finding |
| --- | --- | --- |
| Customer frontend | Starter only | `demo-repository` contains marketing pages and hard-coded dashboard data; it has no authentication or API integration. |
| Partner storefronts | Missing | No demo e-commerce applications currently exist. |
| Gateway | Partial | It resolves a user, scores a request, creates a payment transfer, and creates a repayment schedule. Redis idempotency and rate limiting are present. |
| Authentication | Missing/inactive | Customer authentication does not exist; partner HMAC and mTLS filters are commented out; User Data permits all requests. |
| User data | Partial | It persists users, transactions, payment references, and repayment-plan mirrors, but current feature calculation includes placeholder values. |
| Credit analysis | Good foundation | Rule-based scoring, reason codes, policy versioning, and decision audit persistence exist. There is no bank adapter or enabled AI/footprint factor. |
| Payment and repayment | Demo implementation | Transfers and charging use simulated providers; repayment schedules and a scheduled worker are present. |
| Deployment and testing | Incomplete | Compose exists but its health check and several API contracts do not match implementation. No automated test suite was found. |

## Immediate Compatibility Blockers

1. The documented checkout request uses `buyer.paymentMethod`, whereas the generated Gateway DTO expects `buyer.cardToken`.
2. Gateway sends `payment` when resolving users, whereas User Data expects `paymentMethod`.
3. Credit Analysis OpenAPI specifies only `userId` and `cartTotal`, whereas its actual model requires a `featureClaim`.
4. Docker Compose probes Credit Analysis at `/actuator/health`, but that service exposes `/_health`.
5. `partnerId` is used as part of Gateway idempotency but is absent from the documented checkout schema.
6. User-facing dashboard APIs for application history, repayment plans, and transactions do not exist.

## Target Demo Architecture

```text
Customer web app ─┐
                  ├─ Gateway / BFF ─┬─ Identity and consent
Demo partner apps ┘                 ├─ User/history service
                                    ├─ Bank-data demo adapter
                                    ├─ Footprint feature service
                                    ├─ Credit-analysis service
                                    ├─ Payment simulation
                                    └─ Repayment service

Partner store backend ── authenticated partner API / webhook flow
```

Use fictional partner identities so the application does not imply a real commercial integration:

- **StreamBox**: subscriptions and digital goods.
- **MarketHub**: general retail.
- **Threadly**: clothing store.

Each store has a cart, seeded customer/order history, and a **Pay with Rocket Credit** flow. The store sends the customer to Rocket Credit checkout and receives the purchase outcome after the decision.

## Completion Plan

### Phase 1 — Stabilize contracts, local runtime, and test data

- Make Gateway OpenAPI the source of truth and regenerate/align all DTOs.
- Define one checkout request containing `partnerId`, `orderId`, amount, items, selected plan, buyer identity, payment-token reference, consent snapshot, and idempotency key.
- Return a safe response with `APPROVED`, `REJECTED`, or `REVIEW`, approved amount, repayment schedule, safe reason codes, and trace ID.
- Fix the Credit Analysis health probe and add readiness checks for dependencies.
- Add deterministic seed data for approved, rejected, review, and reduced-offer cases.
- Add API contract tests and a runnable smoke-test script.

**Deliverable:** one working, documented `POST /checkout` flow through every backend service.

### Phase 2 — Identity, roles, and consent

- Add an OIDC identity provider, such as Keycloak, using Authorization Code + PKCE.
- Implement roles: `CUSTOMER`, `PARTNER_ADMIN`, and `OPERATOR`.
- Make Gateway validate JWTs; protect User Data and all internal service endpoints.
- Authenticate demo partner backends with per-partner credentials. Browser clients never receive partner secrets.
- Store consent records with purpose, version, timestamp, withdrawal time, and request ID.
- Exclude bank and footprint factors when their consent is absent; never silently collect data.

**Deliverable:** protected login, role-aware routes, consent management, and an auditable consent trail.

### Phase 3 — Customer web application

Turn `demo-repository` into the Rocket Credit customer app with these routes:

- `/login` and `/register`
- `/dashboard`
- `/checkout` and `/checkout/result`
- `/applications/:id`
- `/repayments`
- `/profile`
- `/privacy-and-consent`

The dashboard must show real API data: purchases by partner, application history, active/completed plans, upcoming installments, and consent state. User-facing explanations must be readable rather than expose only raw model scores.

**Deliverable:** a customer can sign in, request financing, see a decision, and view repayment history.

### Phase 4 — Demo storefronts and partner portal

Build StreamBox, MarketHub, and Threadly as lightweight storefront apps. Each includes a catalog, cart, seeded customer, historical orders, and Rocket Credit checkout handoff.

Add a small partner portal with checkout history, trace IDs, sandbox credentials, webhook delivery logs, and an event replay control.

**Deliverable:** an examiner can start in any demo store, pay through Rocket Credit, then return to the store with the result.

### Phase 5 — Data model and customer APIs

Add migrations for:

- applications and decisions;
- normalized partner orders/history features;
- bank-data and footprint consent;
- consent ledger;
- model and policy version snapshots;
- decision audit records;
- user-facing application and repayment queries.

Expose only Gateway customer APIs:

- `GET /me/dashboard`
- `GET /me/applications`
- `GET /me/repayment-plans`
- `GET /me/transactions`
- `PUT /me/consents`
- `POST /checkout`

Keep raw partner data separate from derived scoring features. Pseudonymize partner identity values before cross-partner aggregation.

### Phase 6 — Partner and bank-data adapters

Use deterministic mock adapters, not real banking credentials. The bank adapter returns only derived signals: income estimate, income stability, disposable income, existing obligations, and balance trend. Partner adapters normalize order history, refunds, tenure, order velocity, and repayment outcomes.

Store data source, collection time, consent version, and feature-schema version with each feature set.

### Phase 7 — Consent-first AI/footprint factor

Do not scrape social media. Implement a permitted footprint feature service based on:

- verified email and phone;
- account age;
- checkout/session consistency;
- abnormal order velocity;
- demo device/session trust;
- partner-account consistency.

Begin with deterministic scoring for repeatable tests. Then add an offline-trained model using synthetic labelled data. A logistic-regression or monotonic gradient-boosted model is more suitable than an LLM for structured credit data.

Use a hybrid decision:

```text
Hard eligibility rules
+ affordability cap
+ versioned weighted scoring
+ bounded explainable AI risk signal
= APPROVED / REJECTED / REVIEW + possible amount
```

Persist the model version, feature version, policy weights, immutable feature snapshot/reference, factor contributions, reason codes, final decision, and later human-review outcome.

### Phase 8 — Concurrency and reliability research

- Resolve identity first, then fetch independent feature groups in parallel.
- Use bounded executors or Java 21 virtual threads; do not use unbounded thread creation.
- Apply timeouts, retries with jitter, circuit breakers, and bulkheads per downstream service.
- Use idempotency keys for checkout, payment, and repayment creation.
- Use a transactional outbox for decision, payment, and repayment events.
- Let multiple scheduler replicas claim due installments safely with transactional row locking such as `SKIP LOCKED`.

Measure sequential versus parallel feature collection using p50/p95 latency, throughput, timeout rate, and recovery behaviour. Demonstrate the safe fallback when the AI service times out.

### Phase 9 — Safe data handling

- Use an identity provider for password storage; do not store passwords in application services.
- Store only fake payment-provider tokens or vault references, never raw card data.
- Encrypt high-value PII at rest and keep keys/secrets outside source control.
- Restrict internal endpoints to authenticated services.
- Retain raw partner data only for the defined demo period; retain derived/audit data only as needed.
- Implement consent withdrawal for future decisions and subject-data export/deletion workflows.

### Phase 10 — Tests, observability, and diploma evidence

Add:

- API contract, unit, integration, authorization, idempotency, scheduler-concurrency, browser-flow, and load tests;
- structured logs, trace IDs, metrics, and dashboards;
- a scripted approval, denial, review, AI-timeout, and duplicate-checkout demonstration.

The written diploma should include architecture and sequence diagrams, auth flow, data classification and consent lifecycle, scoring equation, AI feature rationale, audit examples, concurrency measurements, security controls, fairness checks on synthetic cohorts, and prototype limitations.

## Recommended Implementation Order

1. Stabilize contracts, health checks, migrations, and seed data.
2. Add identity, consent, and protected customer APIs.
3. Build customer checkout, result, and dashboard screens.
4. Build three demo storefronts and their handoff flow.
5. Add reliable partner/bank feature adapters.
6. Add the footprint service and hybrid scoring factor.
7. Add concurrency controls, outbox processing, tests, and observability.
8. Finish partner portal and scripted demo.
