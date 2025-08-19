rocket-credit-gateway — Context & Developer Guide

Owner: Platform (Gateway) Team
Purpose: Orchestrate the synchronous /checkout flow across User Data → Credit Analysis → Payment → Repayment and emit non‑blocking events for Notifications.

⸻

1) What this service does
	•	Exposes a single, public HTTP API for partners: POST /checkout.
	•	Validates partner request, normalizes payload, and orchestrates calls to downstream services in the critical path:
	1.	User Data — fetch/ensure normalized user profile and internal userId.
	2.	Credit Analysis — decisioning for BNPL approval/denial.
	3.	Payment — create payment intent/charge if approved.
	4.	Repayment — generate installment schedule after successful payment intent.
	•	Emits/forwards asynchronous events (optional) for Notifications and audit.
	•	Centralizes authN/Z, rate limiting, input validation, idempotency, and observability for the checkout journey.

Why this shape? Customers expect immediate checkout decisions; orchestration gives fast, predictable outcomes in the happy path while we can offload secondary work to event subscribers. (See project architecture notes.)

⸻

2) Repository layout

rocket-credit-gateway/
├── .openapi-generator/
├── src/main/java/com/rocketcredit/gateway/
│   ├── GatewayApplication.java
│   ├── api/
│   │   ├── ApiUtil.java
│   │   ├── CheckoutApi.java
│   │   ├── CheckoutApiController.java
│   │   ├── CheckoutRequest.java
│   │   └── CheckoutResponse.java
│   ├── model/
│   │   └── Installment.java
│   └── service/
│       ├── CheckoutService.java
│       └── CheckoutServiceImpl.java
├── src/main/resources/application.yml
├── Dockerfile
├── pom.xml
└── README.md


⸻

3) Public API

POST /checkout

Request — CheckoutRequest
	•	Minimal fields (authoritative schema in CheckoutRequest.java):
	•	userToken (or partner user handle)
	•	amount, currency
	•	orderId (idempotency scope), cartItems[]
	•	optional PII needed for KYC linking (email, name) — always validated and redacted in logs

Response — CheckoutResponse
	•	On approval: { status: "APPROVED", creditScore, paymentIntentId, plan: { installments[] } }
	•	On denial: { status: "DENIED", reason, creditScore? }
	•	On failure (downstream error): { status: "ERROR", errorCode, message }

Semantics
	•	Idempotency: required header Idempotency-Key; identical requests with the same orderId and key must return the same outcome.
	•	Correlation: response includes traceId (also added to X-Trace-Id).

⸻

4) Orchestration sequence (happy path)

sequenceDiagram
    participant Client
    participant GW as Gateway
    participant UDS as User Data
    participant CAS as Credit Analysis
    participant PS as Payment
    participant RS as Repayment
    participant NS as Notification (async)

    Client->>GW: POST /checkout {userToken, amount, orderId, cartItems}
    GW->>UDS: GET /user-info?userToken=...
    UDS-->>GW: { userId, normalized profile }
    GW->>CAS: POST /analyze-credit { userId, amount }
    CAS-->>GW: { approved: true, score }
    alt approved
      GW->>PS: POST /payments { userId, amount, orderId }
      PS-->>GW: { paymentIntentId, status }
      GW->>RS: POST /repayments { userId, amount, terms }
      RS-->>GW: { plan: installments[] }
      GW-->>Client: 200 CheckoutResponse(APPROVED)
      Note over GW,NS: Publish events for notifications/audit (non-blocking)
    else denied
      GW-->>Client: 200 CheckoutResponse(DENIED)
    end


⸻

5) Configuration

Environment

These URLs are discovered via environment variables (Docker defaults in parentheses):
	•	USER_DATA_URL → internal base URL of User Data (e.g., http://user-data:8080)
	•	CREDIT_ANALYSIS_URL → Credit Analysis (e.g., http://credit-analysis:8082)
	•	PAYMENT_URL → Payment (e.g., http://payment:8083)
	•	REPAYMENT_URL → Repayment (e.g., http://repayment:8084)
	•	NOTIFICATION_URL → Notification (e.g., http://notification:8085)

Other common vars:
	•	SERVER_PORT (default 8080)
	•	SPRING_PROFILES_ACTIVE (e.g., dev, test, prod)
	•	LOG_LEVEL (optional)

application.yml (excerpt)

server:
  port: ${SERVER_PORT:8080}

app:
  urls:
    userData: ${USER_DATA_URL:http://user-data:8080}
    creditAnalysis: ${CREDIT_ANALYSIS_URL:http://credit-analysis:8082}
    payment: ${PAYMENT_URL:http://payment:8083}
    repayment: ${REPAYMENT_URL:http://repayment:8084}
    notification: ${NOTIFICATION_URL:http://notification:8085}

spring:
  jackson:
    default-property-inclusion: non_null
  main:
    allow-bean-definition-overriding: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus


⸻

6) Code walk‑through
	•	GatewayApplication — Spring Boot bootstrap; enables component scanning and actuator.
	•	api/CheckoutApi — Interface (from OpenAPI generation) with the /checkout contract.
	•	api/CheckoutApiController — HTTP adapter: request validation, idempotency key extraction, mapping to service layer.
	•	service/CheckoutService & CheckoutServiceImpl — core orchestration: calls downstream clients, applies retries/circuit breakers, builds CheckoutResponse.
	•	model/Installment — simple value object used in the response plan.
	•	api/ApiUtil — helpers for responses and parameter handling generated by OpenAPI tools.

⸻

7) Reliability & security

Reliability controls
	•	Timeouts per downstream call (e.g., 1–3s) and budgets for the entire request.
	•	Resilience4j circuit breakers & bulkheads around each downstream dependency.
	•	Idempotency (header + orderId cache / persistence) to prevent double charges.
	•	Fallback mapping: user-friendly error surface while preserving root cause in logs.

Security controls
	•	Auth: Accept either OAuth2/JWT from partners or HMAC-signed requests (configurable). Reject unsigned requests.
	•	Least PII: Only pass minimal identifiers between services; redact PII in logs; never log raw card/passport numbers.
	•	Input validation: Bean Validation (amount ≥ 0, currency ISO‑4217, orderId format, email syntax if present).
	•	mTLS (optional internal): service-to-service within the cluster.
	•	Rate limiting: token bucket per partner key.
	•	Audit trail: structured logs with partnerId, traceId, decision outcome.

⸻

8) Observability
	•	Structured logging with correlation/partner fields.
	•	Metrics: request counts, durations, approval rate, downstream error rates.
	•	Tracing: OpenTelemetry instrumentation spans across UDS/CAS/Payment/Repayment.
	•	Health: /actuator/health and readiness endpoints.

⸻

9) Running locally

With Docker Compose
	1.	docker compose up -d from the root deployment repo.
	2.	Gateway listens on http://localhost:8080 and talks to internal service hostnames on the Docker network.

With Maven

mvn spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-DUSER_DATA_URL=http://localhost:8081 -DCREDIT_ANALYSIS_URL=http://localhost:8082 -DPAYMENT_URL=http://localhost:8083 -DREPAYMENT_URL=http://localhost:8084 -DNOTIFICATION_URL=http://localhost:8085"

Docker image

docker build -t rocket-credit-gateway:latest .
# Run exposing port 8080
docker run --rm -p 8080:8080 \
  -e USER_DATA_URL=http://host.docker.internal:8081 \
  -e CREDIT_ANALYSIS_URL=http://host.docker.internal:8082 \
  -e PAYMENT_URL=http://host.docker.internal:8083 \
  -e REPAYMENT_URL=http://host.docker.internal:8084 \
  -e NOTIFICATION_URL=http://host.docker.internal:8085 \
  rocket-credit-gateway:latest


⸻

10) Example requests

curl -X POST http://localhost:8080/checkout \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f1b8a5e-2a77-4f0b-8c67-1b6d4a4f1a11' \
  -d '{
    "userToken": "abc123",
    "amount": 250.00,
    "currency": "USD",
    "orderId": "ORD-100045",
    "cartItems": [{"sku":"TICKET-RTE-ITA-001","qty":1,"price":250.00}]
  }'


⸻

11) Testing strategy
	•	Unit tests: controller validation, service orchestration, error mapping.
	•	Contract tests: generated from OpenAPI; ensure CheckoutRequest/Response stability.
	•	Integration tests: Testcontainers to spin dependent services or WireMock stubs.
	•	Load tests: focus on /checkout percentile latencies and approval path throughput.

⸻

12) Failure modes & mappings

Failure	HTTP	Response.status	Notes
Validation error	400	ERROR	Constraint violations surfaced with field paths
Unauthorized	401	ERROR	Missing/invalid token or signature
Rate limited	429	ERROR	Include Retry-After
UDS timeout	502	ERROR	Downstream name + retry policy in logs
CAS denies	200	DENIED	Reason string provided
Payment declined	200	ERROR	Map PSP error code; do not expose raw codes to client


⸻

13) Roadmap knobs
	•	Choreography: publish CheckoutInitiated, CreditApproved, PaymentIntentCreated, RepaymentScheduleCreated to an event bus; make Notification fully async.
	•	Policy engine: externalize credit/checkout policies for A/B tests.
	•	mTLS & WAF in front of Gateway for partners in prod.
	•	Cache UDS lookups with short TTL to reduce repeated calls in rapid re-submits.

⸻

14) Ownership & SLAs
	•	SLO: p95 checkout ≤ 800ms (approved path, warm cache), availability ≥ 99.9% monthly.
	•	Pager rotation: Platform/Gateway.

⸻

15) References
	•	Downstream API base URLs configured via env; Compose provides sensible defaults.
	•	End-to-end synchronous flow definition and service responsibilities are aligned with the platform architecture documents.