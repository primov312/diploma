# Rocket Credit Full Web Application Plan

## Short Vision

Rocket Credit should become a complete BNPL web application where customers can check out with installments, partners can integrate Rocket Credit into their stores, and the platform can improve credit decisions with explainable AI signals from consented digital footprint data.

## Current System

The repo already contains the backend foundation:

- `rocket-credit-gateway`: public checkout API that coordinates the purchase flow.
- `rocket-credit-user-data`: stores and serves customer profile and history data.
- `rocket-credit-analysis`: makes approve/deny credit decisions with explainable scoring.
- `rocket-credit-payment`: creates and tracks payment intents.
- `rocket-credit-repayment`: creates installment schedules.
- `rocket-credit-deployment`: Docker Compose and shared deployment configuration.
- `demo-repository`: existing React/Vite frontend starter.

## Proposed Additions

### 1. Customer Frontend

Turn `demo-repository` into the customer-facing Rocket Credit web app.

Core screens:

- Checkout page with cart amount, installment options, consent, and final confirmation.
- Credit decision result page with approved, denied, or review states.
- Customer dashboard with active plans, upcoming repayments, and payment status.
- Profile page for KYC, income, contact details, and consent management.

Frontend responsibilities:

- Call the Gateway instead of directly calling internal services.
- Show clear repayment terms before confirmation.
- Collect explicit consent before using any AI or digital footprint signals.
- Display decision outcomes without exposing internal model internals.

### 2. Partner Frontend / Portal

Add a partner-facing portal for merchants.

Core screens:

- API key and environment management.
- Webhook endpoint configuration.
- Checkout logs and trace IDs.
- Order-level status lookup.
- Sandbox testing tools.

This can live in the same frontend app at first, separated by routes and roles.

### 3. AI Digital Footprint Analysis Service

Add a new backend service, for example:

```text
rocket-credit-footprint-ai/
```

Purpose:

Analyze consented digital footprint signals and convert them into safe, explainable features for credit decisioning.

Allowed signal examples:

- Email or phone verification quality.
- Account age and consistency signals.
- Device/session risk indicators.
- Partner checkout behavior patterns.
- Optional connected-account metadata when explicitly consented.

Signals to avoid unless legal review approves them:

- Sensitive attributes such as race, religion, health, political views, or precise location history.
- Raw social media content scraping.
- Unconsented browser, device, or third-party tracking.
- Any feature that cannot be explained or audited.

Recommended service contract:

```http
POST /footprint/analyze
```

Request:

```json
{
  "userId": 123,
  "orderId": "ORD-100045",
  "cartTotal": 250.0,
  "consent": {
    "digitalFootprint": true,
    "acceptedAt": "2026-05-05T12:00:00Z",
    "version": "v1"
  },
  "signals": {
    "emailVerified": true,
    "phoneVerified": true,
    "accountAgeDays": 420,
    "deviceTrustScore": 0.82,
    "partnerOrderVelocity24h": 1
  }
}
```

Response:

```json
{
  "score": 0.74,
  "riskLevel": "LOW",
  "reasons": [
    "EMAIL_VERIFIED",
    "STABLE_ACCOUNT_HISTORY",
    "LOW_ORDER_VELOCITY"
  ],
  "modelVersion": "footprint-ai-v1",
  "featuresVersion": "digital-footprint-v1"
}
```

### 4. Credit Analysis Integration

Extend `rocket-credit-analysis` with a new optional factor:

```text
Digital Footprint AI
```

Suggested initial weights:

- Rocket history: 40%
- Partner history: 25%
- Amount affordability: 20%
- Digital footprint AI: 15%

Important behavior:

- If consent is missing, do not call the AI service.
- If the AI service fails, continue with the existing scoring model.
- Store the AI score, model version, feature version, and reason codes in the existing audit trail.
- Keep policy weights DB-driven through `credit_policy_versions`.

### 5. Data And Audit Model

Add audit fields to the credit decision record:

- `consent_snapshot`
- `ai_model_version`
- `ai_features_version`
- `digital_footprint_factors`
- `digital_footprint_reasons`

The system should store derived features and reason codes, not raw sensitive data.

### 6. Gateway API Changes

Extend `/checkout` to accept frontend checkout context and consent metadata.

Example:

```json
{
  "userId": 123,
  "cartTotal": 250.0,
  "orderId": "ORD-100045",
  "installmentPlan": "PAY_IN_2",
  "consent": {
    "terms": true,
    "digitalFootprint": true,
    "privacyPolicyVersion": "2026-05-05"
  }
}
```

The Gateway should pass only the necessary fields to Credit Analysis and downstream services.

## Target Architecture

```text
Frontend
   |
   v
Gateway
   |
   +--> User Data
   +--> Credit Analysis
           |
           +--> Footprint AI
   +--> Payment
   +--> Repayment
```

## Implementation Phases

### Phase 1: Frontend MVP

- Rename or promote `demo-repository` as the main frontend.
- Add checkout, decision result, and customer dashboard routes.
- Connect checkout form to `POST /checkout`.
- Add consent UI and client-side validation.

### Phase 2: API Contract Upgrade

- Update Gateway request/response models.
- Add OpenAPI fields for `orderId`, `installmentPlan`, and `consent`.
- Pass consent and checkout context to Credit Analysis.

### Phase 3: AI Service MVP

- Create `rocket-credit-footprint-ai` as a small FastAPI service.
- Start with deterministic feature scoring before using a real ML model.
- Return score, reason codes, and model metadata.
- Add Dockerfile and Docker Compose entry.

### Phase 4: Scoring Integration

- Add `score_digital_footprint` to `rocket-credit-analysis`.
- Add DB policy support for the new weight.
- Persist AI factors and reasons in the audit trail.
- Add tests for consent missing, AI timeout, and successful AI scoring.

### Phase 5: Partner Portal

- Add partner dashboard routes.
- Show checkout traces, webhook status, and sandbox keys.
- Add role-based navigation.

## Compliance Requirements

This feature must be consent-first and explainable:

- Users must opt in before digital footprint analysis is used.
- Decisions must remain explainable with stable reason codes.
- Raw sensitive data should not be stored in Credit Analysis.
- Users should be able to revoke consent for future decisions.
- AI output should influence the model only as one factor, not as an opaque final decision.

## Recommended Next Step

Start with Phase 1 and Phase 2 together: build the frontend checkout flow and update the Gateway contract to carry consent and order context. Then add the AI service behind a feature flag so the existing checkout flow remains stable while the new model factor is tested.
