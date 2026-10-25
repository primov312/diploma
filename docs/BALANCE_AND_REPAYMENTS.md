# Balance and repayments

The demo now executes simulated purchases and credit repayments inside the Java backend. It uses the existing PostgreSQL database, session authentication and CSRF protection. No real funds, banks, payment processors or merchant transfers are involved.

## Customer flows

- `/balance`: available balance, demo top-ups, upcoming/overdue obligations, daily balance and cash-flow charts, and recent activity.
- `/history`: the existing history page, expanded to purchases, top-ups, repayments and early settlements. Historical fixtures do not affect wallet balances.
- `/repayments`: installment filters, per-credit detail, manual payment of the oldest due installment, and early settlement. Use `?loanId=123` to select a credit.
- Store catalogs provide **Pay from balance**, including login and top-up return links.
- `/apply` requires purchase details, a term selection and a server-generated quote. Submitting saves a decision. **Accept credit and pay store** creates the financed purchase and installments only after approval.

An approved credit pays the selected store; it does not deposit money in the customer wallet. New wallets start at zero. Refund execution, withdrawals, transfers and partial principal prepayments are not implemented.

## Pricing contract

Money is stored in USD cents. New payment endpoints serialize money as decimal strings. The frontend retains the existing fixed 360 HUF/USD illustration, displays rounded HUF, and shows exact USD on confirmations. Money is never calculated by adding chart/display values.

Terms are 1–3 weekly installments or 1–24 monthly installments. The default nominal annual rate is 0.12. Periodic rate is annual rate / 52 or annual rate / 12. Payments use the standard annuity formula, decimal arithmetic and HALF_UP rounding; the last payment clears principal exactly. Zero-rate configurations are supported. Tiny amounts that cannot allocate positive principal to every installment are rejected.

The first payment is one full period after acceptance. Monthly dates are anchored to the original date and clamped independently for short months. Due dates use Europe/Budapest; timestamps are stored in UTC. There are no down payments, late fees or additional overdue interest.

Quotes expire after 15 minutes or at Budapest midnight. An approved offer remains acceptable through its quoted business date while financial generation, pricing version, product price and store cap remain valid. Historical decisions without a quote cannot be activated.

Early settlement charges outstanding principal plus unpaid earned interest. Completed periods earn their original scheduled interest, the current period earns a calendar-day fraction, and future periods earn none. Aggregate earned interest is rounded once to cents. This is a demo convention, not a banking APR calculation. Settlement quotes expire after five minutes, at midnight, or on any loan version change. Original scheduled amounts and waived interest remain visible after closure.

## Accounting and collection

A wallet balance is a materialized ledger total. Every successful top-up, debit, purchase and loan update commits atomically with its operation result. The ledger is append-only in application code. Reconciliation compares balances to ledger sums hourly and logs any mismatches.

All mutations lock financial state, wallet, then loan/installments in stable order. Idempotency keys are scoped to the authenticated user. A matching retry returns its recorded result; another payload with the same key returns `IDEMPOTENCY_CONFLICT`. Insufficient-funds outcomes are recorded: after funding, use a new key for a new attempt. Technical failures roll back and can safely retry the same key.

Automatic collection polls every minute, including missed dates after a restart. It collects full installments ordered by due date, loan ID and sequence, stopping for that user when the oldest cannot be funded. Funds are not reserved. Manual payments and early settlement use the same locks as the worker. Failed collection attempts are separate from ledger entries.

Affordability includes external monthly debt payments plus active Rocket Credit obligations. Each loan contributes the greater of its monthly-equivalent installment and unpaid installments due through the next calendar month. Repayment, settlement, activation, purchase history changes and daily rollover invalidate outstanding financial generations. Existing legacy scoring contracts and historical snapshots remain available; new contracts have a `-credit-v1` formula suffix. The dashboard is a six-month illustration at the configured rate.

## API

Authenticated reads:

- `GET /api/wallet`, `GET /api/wallet/chart?range=30d|90d|1y`
- `GET /api/history?partner=&kind=&from=&to=&page=0&size=25`
- `GET /api/repayments?loanId=&partner=&status=UNPAID&from=&to=&page=0&size=25`
- `GET /api/loans/{id}`

CSRF-protected writes:

- `POST /api/credit-quotes`: `{partnerSlug, productId? , requestedAmount?, termUnit, installmentCount}`
- `POST /api/applications`: existing request fields plus `quoteId`
- `POST /api/applications/{id}/accept`
- `POST /api/wallet/top-ups`: `{amount: "100.00"}` in USD
- `POST /api/purchases`: `{partnerSlug, productId, expectedAmount?}`; price is resolved from the catalog and compared with the displayed amount when supplied
- `POST /api/repayments/{id}/pay`
- `POST /api/loans/{id}/settlement-quotes`
- `POST /api/loans/{id}/settle`: `{quoteId}`

All writes except quote generation require `Idempotency-Key` (1–120 characters). Ownership comes from the session, never a client user ID. Missing/foreign resource IDs return 404. Pagination supports 1–100 items. Dates are ISO dates.

`/api/transactions` remains the purchase-only API used by scoring. The unified feed deduplicates direct purchases and distinguishes purchase value from balance change. Repayments and top-ups never become purchase-history scoring events.

## Configuration and migration

Spring properties (or relaxed-binding environment equivalents):

| Property | Default |
| --- | --- |
| `rocket.payments.annual-rate` | `0.12` |
| `rocket.payments.collection-enabled` | `true` |
| `rocket.payments.poll-delay` | `60000` ms |
| `rocket.payments.reconciliation-delay` | `3600000` ms |
| `rocket.payments.seed-fixtures` | `false` |

Changing the interest rate changes the pricing version, invalidating unaccepted quotes; existing loans retain their terms. Disable collection temporarily during rollout if needed. Apply V14 through Flyway, deploy the analysis contract and backend together, then the frontend. No legacy approvals or purchases are converted into debt or wallet movements.

Set `rocket.payments.seed-fixtures=true` explicitly to create `repayments@demo.rocket.local` (password `rocket-demo-123`). This separate synthetic account has a $25 ledger-funded wallet and a six-month $300 loan with overdue installments. Collection remains insufficient until a top-up. Repeated startup does not duplicate the fixture or change existing accounts.

## Verification

- Backend: `mvn -f rocket-credit-backend/pom.xml test` (Docker required for disposable PostgreSQL Testcontainers).
- Analysis: from `rocket-credit-analysis`, `.venv/bin/python -m pytest -q`.
- Frontend: from `demo-repository`, `npx tsc --noEmit` and `npm run build`.
- Browser: `BASE_URL=http://localhost:5173 npx playwright test` against a running isolated stack. `e2e/payments.spec.ts` exercises real top-ups, purchases, approval/acceptance and settlement. It changes only simulated money.
- For an isolated backend port, set `ROCKET_API_PROXY=http://127.0.0.1:18080` when starting Vite.

Java and Python share `research/fixtures/repayment-golden.json`. Tests also cover calendar boundaries, rounding, debt capacity, rollback, overspending, ownership, stale terms, worker/manual races, early settlement races and collection restart catch-up.
