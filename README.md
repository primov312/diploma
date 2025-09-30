# Rocket Credit Repayment Service

This service creates BNPL repayment plans and maintains installment schedules. It now notifies the User Data Service (UDS) whenever a new plan is created so UDS can mirror plan metadata for analytics and features.

## User-Data Notification

- Endpoint: UDS `POST /internal/repayment/plan-created`
- Auth: HMAC-SHA256 via headers `X-Timestamp`, `X-Nonce`, `X-Signature`.
- Idempotency: `X-Nonce` is the `planUid`; UDS deduplicates via unique DB constraints.

### Configuration

- `USER_DATA_URL` (default `http://user-data:8080`): Base URL of UDS.
- `UDS_WEBHOOK_SECRET` (no default): Shared secret for HMAC.

The notifier sends a compact JSON payload with `planUid`, `userId`, `totalAmount`, `currency`, `installments`, and `schedule` items.
