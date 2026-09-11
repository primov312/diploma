Partner Integration Kit — Rocket Credit (CreDIT)

Who this is for: engineering teams at partner e‑commerce sites integrating Rocket Credit.
Surfaces you’ll use: JavaScript SDK (frontend), Gateway REST /checkout (via frontend or partner backend), Webhooks (backend), and optionally a Partner Portal for keys/webhooks/logs.

⸻

1) Quick start

Two supported patterns:
	1.	Browser → Gateway via SDK (recommended) using a short‑lived clientToken minted by your backend.
	2.	Browser → Partner Backend → Gateway (server‑to‑server proxy) for shops that don’t allow third‑party CORS.

Environments
	•	Sandbox base URL: https://sandbox.api.rocketcredit.example
	•	Production base URL: https://api.rocketcredit.example

Auth
	•	Server secrets: ROCKET_SECRET_KEY + X-Partner-Id → used only from your backend.
	•	Client token: JWT minted by /v1/sessions, scoped to orderId, amount, and allowed origins[].

⸻

2) Public APIs

2.1 Sessions (server → Gateway)

POST /v1/sessions

{
  "orderId": "ORD-100045",
  "amount": 250.00,
  "currency": "USD",
  "origins": ["https://shop.partner.example"],
  "customerHint": {"email": "alice@example.com"}
}

Headers: Authorization: Bearer <ROCKET_SECRET_KEY>, X-Partner-Id: <id>
Response 200

{"clientToken":"<jwt>","publishableKey":"pk_live_xxx","expiresAt":"2025-08-20T12:00:00Z"}

2.2 Checkout (frontend or server → Gateway)

POST /checkout

{
  "userToken": "abc123",
  "amount": 250.00,
  "currency": "USD",
  "orderId": "ORD-100045",
  "cartItems": [{"sku":"SKU-1","qty":1,"price":250.00}]
}

Headers (frontend path): Authorization: Bearer <clientToken>, Idempotency-Key: <uuid>, Content-Type: application/json
Response

{
  "status": "APPROVED",
  "paymentIntentId": "pi_123",
  "installments": [{"sequence":1,"dueDate":"2025-09-01","amount":125.00}],
  "creditScore": 712,
  "traceId": "trc_abc"
}

2.3 Plans (optional pre‑quote)

GET /plans?amount=250.00&currency=USD → returns candidate installment schedules.

⸻

3) Webhooks (backend)

Endpoint you host: POST https://partner.example/rocketcredit/webhooks

Events (subject to change w/ versioning):
	•	credit.approved
	•	credit.denied
	•	payment.intent.created
	•	payment.captured
	•	payment.failed
	•	repayment.schedule.created
	•	repayment.installment.due
	•	repayment.installment.paid
	•	repayment.installment.failed

Delivery
	•	Retries with exponential backoff (max 24h).
	•	Event idempotency via eventId + X-Rocket-Signature header.

Signature (HMAC‑SHA256)

X-Rocket-Timestamp: 1724150400
X-Rocket-Signature: t=v1,sig=hex(hmac(secret, timestamp + "." + body))

Verify: reject if clock‑skew > 5m or signature mismatch; respond 2xx to ack.

Sample payload

{
  "eventId":"evt_9c3",
  "type":"payment.captured",
  "created":"2025-08-20T08:01:22Z",
  "data":{
    "orderId":"ORD-100045",
    "paymentIntentId":"pi_123",
    "amount":250.00,
    "currency":"USD"
  }
}


⸻

4) SDK (browser)

<script src="https://cdn.rocketcredit.example/sdk/v1/credit.js"></script>
<script>
  const { clientToken, publishableKey } = await fetch("/bnpl/session", {method:"POST", body: JSON.stringify(order), headers:{"Content-Type":"application/json"}}).then(r=>r.json());
  const credit = window.RocketCredit.init({ publishableKey, clientToken, environment: "sandbox" });
  const result = await credit.checkout({ amount: order.amount, currency: order.currency, orderId: order.orderId, items: order.items });
</script>


⸻

5) Errors & idempotency

Idempotency
	•	Send Idempotency-Key per unique orderId.
	•	Retries with the same key return the original decision.

Error taxonomy (subset)

code	http	message	client action
validation_failed	400	field errors array	fix request and retry
unauthorized	401	invalid token	refresh session or rotate keys
rate_limited	429	backoff suggested	retry w/ jitter + same key
downstream_unavailable	502	temporary outage	retry with same key
payment_declined	200	PSP decline normalized	offer other method


⸻

6) Compliance & UX
	•	Disclosures: show total cost, APR/fees (if any), installment schedule, and T&Cs link before confirmation.
	•	Consent logging: SDK emits consent.accepted event in the audit stream; you store consent timestamp/user context on your side too.
	•	Accessibility: BNPL button and widget meet WCAG AA (keyboard/focus states provided by SDK).

⸻

7) Security requirements
	•	Secrets never in browser. Use /v1/sessions from your backend.
	•	CORS: only pre‑registered partner domains allowed. Provide list during onboarding.
	•	Webhook security: validate HMAC, enforce TLS 1.2+, IP allowlist optional.
	•	PII minimization: send only what’s required (email, name optional); no PANs handled by Rocket Credit.
	•	IdP linking (optional): if you pass a stable userToken, hash/email‑salt best practice.

⸻

8) Onboarding checklist (what we need from you)
	•	Business details & technical POC
	•	Domain(s) for CORS allowlist
	•	Webhook URL(s) + backup URL (optional)
	•	Sandbox merchant account created (we provision)
	•	Keys exchanged securely (portal or secure channel)
	•	Pick integration pattern (SDK or server proxy)
	•	Run Conformance Suite (see §10) and submit logs

⸻

9) Operational
	•	SLA: 99.9% monthly for /checkout (p95 ≤ 800ms in region).
	•	Status page: https://status.rocketcredit.example
	•	Support: support@rocketcredit.example (24×5), Pager for P1 incidents.
	•	Versioning: Semantic. Breaking changes announced ≥90 days; dual‑run when possible.

⸻

10) Conformance Suite (self‑cert)

Run these tests in sandbox and attach results:
	1.	Approve flow (happy path) returns installments & paymentIntentId.
	2.	Denial flow mapped to DENIED with reason.
	3.	PSP decline mapped to ERROR/payment_declined.
	4.	Idempotency: re‑POST with same key → identical response.
	5.	Webhook delivery and signature verification.
	6.	Webhook retry handling (respond 500 twice, then 200).
	7.	Large cart (10+ lines) within payload limits.
	8.	Rate limit backoff: respect Retry-After.
	9.	Timeout handling: user‑visible message + retry with same key.
	10.	Token expiry refresh logic.

⸻

11) Deliverables (what we provide to you)
	•	OpenAPI (gateway/openapi.yaml) for /v1/sessions, /checkout, /plans.
	•	Postman/Insomnia collections with sandbox environment file.
	•	SDKs: JS (browser), thin server wrappers (Node, Java, Python).
	•	Sample apps:
	•	examples/js-checkout (SDK pattern)
	•	examples/server-proxy (Express or Spring Boot)
	•	Webhook verifier: CLI and snippet libs.
	•	Event catalog: JSON Schemas for each webhook.

⸻

12) Repo pointers (for internal maintainers)
	•	rocket-credit-gateway: host /partners folder: docs/, openapi/, postman/, examples/, schemas/
	•	rocket-credit-deployment: sample docker-compose and envs for partner sandboxes.
	•	Other services remain internal; partners only use Gateway + Webhooks.

⸻

13) Sample server proxy (Java/Spring)

@RestController
@RequestMapping("/bnpl")
public class BnplController {
  private final WebClient gw = WebClient.builder()
    .baseUrl(System.getenv("ROCKET_BASE_URL"))
    .defaultHeader("X-Partner-Id", System.getenv("ROCKET_PARTNER_ID"))
    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + System.getenv("ROCKET_SECRET_KEY"))
    .build();

  @PostMapping("/session")
  public Mono<Map<String,Object>> session(@RequestBody Map<String,Object> body){
    return gw.post().uri("/v1/sessions").bodyValue(body).retrieve().bodyToMono(new ParameterizedTypeReference<>(){});
  }

  @PostMapping("/checkout")
  public Mono<Map<String,Object>> checkout(@RequestBody Map<String,Object> body, @RequestHeader(value="Idempotency-Key", required=false) String idem){
    return gw.post().uri("/checkout")
      .headers(h -> h.add("Idempotency-Key", Optional.ofNullable(idem).orElse(UUID.randomUUID().toString())))
      .bodyValue(body).retrieve().bodyToMono(new ParameterizedTypeReference<>(){});
  }
}


⸻

14) Data & privacy (DPA summary)
	•	Processor role for checkout/repayment notifications; partners remain Controllers of their consumer data.
	•	Data retention: 24 months for transaction records; webhook logs 30 days.
	•	Deletion: GDPR/CCPA erase requests honored via Controller request with verified subject.

⸻

15) Change log
	•	v0.9 (current): Initial partner kit with SDK + REST + webhooks.
	•	v1.0 (planned): Partner Portal (keys, webhook UI, event replays), statement exports API.