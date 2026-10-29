<!-- AI-assisted (Claude), 2026; reviewed by the author. -->
# Three kinds of users

Rocket Credit has three kinds of users. Each has its own screens, and the server enforces each role on
every request. Diagrams: [ROLES_DIAGRAMS.md](ROLES_DIAGRAMS.md). Walkthroughs: [DEMO_SCENARIOS.md](DEMO_SCENARIOS.md#seller-and-administrator-walkthroughs).

| Role | Who | Start page | How the account is created |
| --- | --- | --- | --- |
| `CUSTOMER` | a person buying from a store | `/account-dashboard` | public sign-up (always this role) or demo seed |
| `SELLER` | a partner's staff (Netflix, Zara, Amazon) | `/seller` | an administrator, or the demo seed |
| `ADMIN` | Rocket Credit operator | `/admin` | demo seed only |

## What each role can do

**Customer** (unchanged): marketplace, dashboard, credit applications and decisions, wallet, repayments, privacy and consents.
A customer now also sees the result of a human review on the decision page and in the application list.

**Seller**: only its own partner.
- *Overview*: credit requests that cite the store, by decision; financed orders and their total principal (what Rocket Credit pays the store in this simulation); active and closed credits; orders per day for 30 days.
- *Orders*: each financed order (loan) with product, principal, plan, status and date. The customer is shown only as a pseudonym `C-XXXXXX`.
- *Catalogue*: add and edit products, switch them on or off. Name 1–160 characters, description 1–500, price above 0 and at most the partner's financing limit, a category and an image from the existing sets (no uploads).

**Administrator**:
- *Overview*: accounts by role and state, decisions, open and decided reviews, active credits, overdue instalments, service health.
- *Users*: list and search; create a seller for a partner; disable and enable accounts; set a temporary password. Roles cannot be edited.
- *Reviews*: decide requests the automatic check left in `REVIEW` (human intervention, below).
- *Partners*: display name, financing limit, active flag.
- *Audit log*: every administrator and seller change, read-only.

## Permission matrix

`✓` allowed · `401` no session · `403` wrong role (`FORBIDDEN_ROLE`). Tested by `RoleAccessTest.permissionMatrix`.

| Area | Example endpoint | Anonymous | Customer | Seller | Admin |
| --- | --- | --- | --- | --- | --- |
| Public | `GET /api/catalog`, `/api/partners`, `/api/health`, `/api/csrf`, sign-up, sign-in | ✓ | ✓ | ✓ | ✓ |
| Own account | `GET /api/me` | 401 | ✓ | ✓ | ✓ |
| Customer | `/api/me/**`, `/api/applications/**`, `/api/wallet/**`, `/api/purchases`, `/api/credit-quotes`, `/api/repayments/**`, `/api/loans/**`, `/api/history`, `/api/transactions/**`, `/api/address/**`, `/api/local-costs` | 401 | ✓ | 403 | 403 |
| Seller | `/api/seller/**` | 401 | 403 | ✓ | 403 |
| Admin | `/api/admin/**` | 401 | 403 | 403 | ✓ |
| Anything else under `/api` | e.g. `/api/somewhere-new` | 401 | 403 | 403 | 403 |

| Screen | Customer | Seller | Admin |
| --- | --- | --- | --- |
| Marketplace (browse) | ✓ | ✓ | ✓ |
| Marketplace: "Apply for credit", "Pay now" | ✓ (signed-out visitors are sent to sign in) | hint only | hint only |
| Dashboard, Applications, Wallet, Privacy | ✓ | → `/seller` | → `/admin` |
| `/seller`, `/seller/orders`, `/seller/catalog` | → dashboard | ✓ | → `/admin` |
| `/admin`, `/admin/users`, `/admin/reviews[/:id]`, `/admin/partners`, `/admin/audit` | → dashboard | → `/seller` | ✓ |

A signed-in user who opens another role's page is sent to their own start page with a short notice.
The screens only help: the server refuses the same calls.

## What each role can see

| Data | Customer | Seller | Admin |
| --- | --- | --- | --- |
| Own financial inputs, address, footprint, consents, wallet | own only | – | – |
| Customer e-mail or name | own only | – | account list (to manage accounts) |
| Customer reference in orders | – | pseudonym `C-XXXXXX` | account ID in reviews |
| Decision outcome, amounts, reason codes, factor scores | own | – | REVIEW applications only |
| Factor details (income and cost figures), feature snapshot | own | – | – |
| Financed orders (loan ID, product, principal, plan, status) | own | own partner | counts only |
| Password hash, tokens | never | never | never |

## Security decisions

1. **Deny by default.** Every `/api` prefix is listed in `SecurityConfig` with its role; an unlisted one is refused (`denyAll`).
2. **The client never chooses its role or partner.** Sign-up has no role, partner or enabled field and ignores unknown JSON properties, so it always creates a customer. Seller endpoints have no partner parameter: the partner comes from the session principal (`AuthenticatedUser.partnerId`). Another partner's product answers `404`, so its existence does not leak.
3. **Account changes take effect at once.** Sessions live in memory. `AccountStateFilter` re-reads the account (role, enabled, partner) on every signed-in API request; on any difference it invalidates the session and the request continues as anonymous (`401`). This costs one primary-key query per request and needs no session registry. A disabled account cannot sign in and gets the same `INVALID_CREDENTIALS` answer as a wrong password.
4. **Seller links are consistent in the database.** Deferred constraint triggers make sure a `seller_accounts` row exists exactly for users with role `SELLER` (checked at commit).
5. **Pseudonymised customers for sellers** (GDPR Art. 5(1)(c), Art. 4(5)). `C-` plus six hex characters of HMAC-SHA256 over partner and user ID, keyed with the existing analysis shared secret. Different stores get different references for the same customer, so they cannot link their lists.
6. **Append-only audit.** Every seller and administrator change writes one `audit_events` row in the same transaction (`AuditLog` requires a running transaction). A trigger refuses `UPDATE` and `DELETE`; there is no write endpoint. Details hold IDs, amounts, flags and names only: no passwords, hashes, tokens or free text about customers (the review note stays in `application_reviews`).
7. **Human review** (GDPR Art. 22(3); recital 56 of Directive (EU) 2023/2225). An administrator decides a `REVIEW` application once: `APPROVED` with `0 < amount ≤ min(possible, requested)`, or `REJECTED`; a note of 10–500 characters is required. The automated decision (status, score, reasons, factors) is never changed; the human decision is a separate, append-only row shown beside it. A row lock and `UNIQUE(application_id)` turn a double submission into `409 ALREADY_REVIEWED`. The customer still accepts through the normal flow; the loan then uses the approved amount with the customer's chosen term, recalculated from the day of acceptance, and the usual checks still apply (financial information unchanged since the application, pricing version, partner and product still offered at the quoted price, amount within the partner limit).
8. **Catalogue switches.** Inactive partners and products disappear from `/api/catalog` and `/api/partners`; quotes, applications and purchases for them answer `409 PARTNER_UNAVAILABLE` / `PRODUCT_UNAVAILABLE`. Price changes never rewrite quotes, applications or loans; a pending offer at the old price fails with `TERMS_CHANGED`.
9. **Staff accounts have no customer data**: no wallet, financial profile, consents or financial state.

## Error codes

Every error has the standard body `{"error": CODE, "message": "…"}`; the screens show `message`.

| Code | HTTP | When |
| --- | --- | --- |
| (no body) | 401 | no session, or the session ended (also right after an administrator disabled the account) |
| `INVALID_CREDENTIALS` | 401 | wrong e-mail or password, or a disabled account (deliberately the same answer) |
| `FORBIDDEN_ROLE` | 403 | signed in, but the route belongs to another role, or the `/api` route is not listed (deny by default) |
| `CSRF_INVALID` | 403 | `X-XSRF-TOKEN` missing or not matching the `XSRF-TOKEN` cookie. Refused before any controller runs; the React client fetches a fresh token and retries once |
| `OWN_ACCOUNT` | 409 | an administrator tries to disable their own account |
| `LAST_ADMIN` | 409 | the change would leave no enabled administrator (two administrators disabling each other at once) |
| `EMAIL_TAKEN` | 409 | sign-up or "create seller" with an e-mail that exists; also the answer to a double-submitted "create seller" |
| `UNKNOWN_PARTNER` | 400 | "create seller" for a partner that does not exist |
| `INVALID_OUTCOME` | 400 | review outcome other than `APPROVED` or `REJECTED` |
| `INVALID_NOTE` | 400 | review note shorter than 10 or longer than 500 characters after trimming |
| `INVALID_APPROVED_AMOUNT` | 400 | approval without an amount, an amount ≤ 0 or above min(possible, requested), or a rejection with an amount |
| `NO_REPAYMENT_PLAN` | 409 | approval of an application that has no quote (made before quotes existed), so nothing could be accepted |
| `ALREADY_REVIEWED` | 409 | the application already has a human decision; also the answer to a double-submitted decision |
| `NOT_FOUND` | 404 | a review for an application that is not in `REVIEW`; another partner's product (existence is not revealed) |
| `PRICE_ABOVE_CAP`, `UNKNOWN_CATEGORY`, `UNKNOWN_IMAGE`, `INVALID_NAME`, `INVALID_DESCRIPTION` | 400 | seller product rules |
| `PARTNER_UNAVAILABLE`, `PRODUCT_UNAVAILABLE` | 409 | quote, application, purchase or acceptance for a switched-off partner or product |
| `NOT_ACTIVATABLE` | 409 | accepting an application with no offer (automatic or human rejection, or an open review) |
| `INVALID_PAGE`, `INVALID_STATUS`, `INVALID_STATE`, `INVALID_ROLE` | 400 | bad list filters |
| `VALIDATION_FAILED` | 400 | bean validation; `fields` names the invalid fields |

## Service health

The administrator overview reuses the actuator health indicators that `GET /actuator/health` also shows:
`db`, `analysis` (`AnalysisHealthIndicator`) and `collector` (`CollectorHealthIndicator`, which calls only the
collector's public `GET /health`). Each answers `UP` or `DOWN`.

## Demo accounts

Password for all: `rocket-demo-123` (one-click buttons on the sign-in page).

| Account | Role | Partner |
| --- | --- | --- |
| `admin@demo.rocket.local` | administrator | – |
| `netflix@demo.rocket.local` | seller | Netflix (`streambox`) |
| `zara@demo.rocket.local` | seller | Zara (`threadly`) |
| `amazon@demo.rocket.local` | seller | Amazon (`markethub`) |
| `avery`, `riley`, `drew`, `casey` `@demo.rocket.local` | customers | – |

So the seller screens are not empty, the seed also creates `orders@demo.rocket.local` ("Synthetic Orders Demo"), a customer
with 12 financed orders (4 per partner, 3 of them settled early). They belong to this separate account, not to the four
personas, because a loan adds monthly obligations and a purchase adds history, which would change the personas' decisions.
The seed is idempotent; an existing account with one of these e-mails but another role is never promoted.

## Code map

| Part | Where |
| --- | --- |
| Schema | `rocket-credit-backend/src/main/resources/db/migration/V17__roles_sellers_admin_audit.sql` |
| Roles, principal, route rules, session check | `auth/SecurityConfig.java`, `auth/AuthenticatedUser.java`, `auth/AccountStateFilter.java`, `users/Role.java` |
| Seller API | `seller/SellerController.java`, `seller/SellerService.java`, `seller/CustomerPseudonyms.java` |
| Admin API | `admin/AdminController.java`, `admin/AdminAccountService.java`, `admin/ReviewService.java`, `admin/AdminPlatformService.java` |
| Audit | `audit/AuditLog.java` |
| Acceptance after review | `payments/PaymentService.accept`, `payments/CreditQuotes.reissueForReview` |
| Seed | `fixtures/StaffDemoData.java` |
| Screens | `demo-repository/src/pages/seller/`, `src/pages/admin/`, `src/auth/RequireRole.tsx`, `src/auth/roles.ts` |
| Tests | `auth/RoleAccessTest`, `seller/SellerIntegrationTest`, `admin/AdminIntegrationTest`, `db/MigrationTest`, `fixtures/DemoDataTest`; Playwright `roles`, `seller`, `admin-review` |
