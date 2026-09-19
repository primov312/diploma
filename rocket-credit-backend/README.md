# rocket-credit-backend

Consolidated Java 21 / Spring Boot 3 backend for the diploma runtime. It replaces the separate
Gateway and User Data services: one process owns authentication, users, partner catalog, transaction
history, credit applications and the call to the Python analysis service.

Packages (`com.rocketcredit.backend`):

| Package | Responsibility |
| --- | --- |
| `auth` | Spring Security: session cookie, CSRF, password encoder (login/register in Step 2) |
| `users` | `users`, `demo_financial_profiles` entities and repositories |
| `partners` | `partners`, `products` catalog |
| `transactions` | seeded per-user purchase history, ownership-scoped queries |
| `applications` | `credit_applications`: request + feature snapshot + decision saved atomically |
| `analysis` | `AnalysisClient` (RestClient, shared-secret header, timeouts) and health indicator |
| `web` | `/api/health`, SPA fallback for the built React app |

Schema: `src/main/resources/db/migration/V1__initial_schema.sql` (Flyway; JPA runs in `validate` mode).

```bash
mvn test                       # unit tests (no database needed)
mvn spring-boot:run            # needs PostgreSQL + analysis; see rocket-credit-deployment/DIPLOMA_RUNTIME.md
```

Environment: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ANALYSIS_URL`, `ANALYSIS_SHARED_SECRET`, `COOKIE_SECURE`.

## Authentication (Step 2)

| Call | Result |
| --- | --- |
| `GET /api/csrf` | sets `XSRF-TOKEN` cookie (readable by JS), returns `{headerName, token}` |
| `POST /api/auth/register` `{email,password,displayName}` | 201 user; 409 `EMAIL_TAKEN`; 400 `VALIDATION_FAILED` (field names only) |
| `POST /api/auth/login` `{email,password}` | 200 user + `RCSESSION` cookie (HttpOnly, SameSite=Lax, Secure if `COOKIE_SECURE=true`); 401 `INVALID_CREDENTIALS` for wrong password **and** unknown email |
| `GET /api/me` | 200 current user; 401 without session |
| `POST /api/auth/logout` | 204, session invalidated |

Every `POST` needs `X-XSRF-TOKEN` equal to the `XSRF-TOKEN` cookie (double-submit); otherwise 403.
Emails are trimmed and lower-cased before storage and lookup (DB `CHECK` enforces it). Passwords are BCrypt
hashes verified by Spring Security's `DaoAuthenticationProvider`; the hash never leaves the database layer.
Logging in from an existing anonymous session rotates the session ID. Sessions live in the Java process:
a restart logs everyone out. Private endpoints take the user ID from the session principal only; a record
that belongs to another user answers 404, the same as a record that does not exist.

Tests: `AuthFlowTest`, `OwnershipTest` (Testcontainers PostgreSQL; needs Docker).
