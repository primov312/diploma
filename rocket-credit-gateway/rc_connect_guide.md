🚀 Rocket Credit Partner Integration Guide

1. Overview

Rocket Credit (“CreDIT”) enables partners to offer Buy-Now-Pay-Later (BNPL) at checkout.
Partners integrate with our Gateway API (/checkout) which orchestrates all backend services:
	•	User Data Service (UDS) → fetches and normalizes customer info
	•	Credit Analysis Service (CAS) → approves/denies BNPL requests
	•	Payment Service → creates a payment intent
	•	Repayment Service → generates installment schedules
	•	Notification Service → sends alerts

To ensure security, all partner calls must use mutual TLS (mTLS). This guarantees only registered partners with valid client certificates can access /checkout.

⸻

2. Connectivity & mTLS Setup

2.1 Certificates

Each partner receives:
	•	Root CA certificate (Rocket Credit’s CA)
	•	Server certificate (our API Gateway, signed by Rocket Credit CA)
	•	Client certificate + private key (your org’s cert signed by Rocket Credit CA)

2.2 Partner Requirements
	•	Store certificates securely (e.g. in a hardware module, Vault, or KMS).
	•	Present your client certificate on every HTTPS request.
	•	Verify the Gateway’s server cert against Rocket Credit’s CA root.

2.3 Example: cURL with mTLS

curl -v https://api.rocketcredit.com/checkout \
  --cert partner.crt \
  --key partner.key \
  --cacert rocketcredit-ca.crt \
  -H "Content-Type: application/json" \
  -d @payload.json


⸻

3. Request: /checkout

Endpoint

POST /checkout
Content-Type: application/json

Example Payload

{
  "partnerId" : "Shidi",
  "partnerPaymentId": "PAY-12345",
  "amount": 50.0,
  "currency": "USD",
  "installmentDurationMonths": 6,
  "buyer": {
    "partnerUserId": "partner-uid-789",
    "email": "alice@company.com",
    "name": "Alice Doe",
    "cardToken": "pm_tok_abc",
    "transactions": [
      { "date": "2025-09-01", "amount": 120.5, "method": "CARD" },
      { "date": "2025-08-15", "amount": 80.0, "method": "BANK_TRANSFER" }
    ],
    "items": [
      { "sku": "ABC", "name": "gun", "price": 40.0, "quantity" : 3 }
    ]
  }
}


⸻

4. Response

Success (200)

{
  "approved": true,
  "reason": null,
  "userId": 42,
  "paymentId": "1",
  "partnerPaymentId": "PAY-12345",
  "repaymentPlanId": "rpln_c32d74bb5ca0",
  "installmentDurationMonths": 6,
  "schedule": [
    { "dueDate": "2025-10-21", "amount": 8.33 },
    { "dueDate": "2025-11-21", "amount": 8.33 }
  ]
}

Denied (200)

{
  "approved": false,
  "reason": "Low credit score",
  "userId": 42,
  "partnerPaymentId": "PAY-12345",
  "schedule": []
}

Duplicate Request (Idempotency, 409 or replay)

If (partnerId, partnerPaymentId) has already been processed, you’ll receive either:
	•	409 Conflict if processing is in progress
	•	200 with cached response if a previous decision exists

⸻

5. Security Requirements
	•	mTLS is mandatory for all /checkout requests.
	•	Idempotency: always send a unique partnerPaymentId per transaction.
	•	Replay protection: Rocket Credit rejects replays with identical payload + signature.

⸻

6. Local Testing with Docker Compose

In local dev (docker-compose.yml ￼), Gateway is exposed on port 8080.
To test mTLS locally:

docker compose exec gateway \
  java -jar app.jar \
  --server.ssl.client-auth=need \
  --server.ssl.key-store=/certs/gateway.p12 \
  --server.ssl.key-store-password=changeit \
  --server.ssl.trust-store=/certs/truststore.p12 \
  --server.ssl.trust-store-password=changeit

