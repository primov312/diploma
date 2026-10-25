# Safe data keeping — what the diploma runtime protects, and how

Updated 2026-10-22 (Facebook, address images and Gemini rows added). Evidence for research topic 4 (see [completion plan](DIPLOMA_COMPLETION_PLAN.md) §4).
This is a local demonstration; it does not claim production certification.

## Protected in the application

| Concern | Mechanism | Where |
| --- | --- | --- |
| Passwords | BCrypt hashes (cost 10) written by Spring Security's `PasswordEncoder`; verified by `DaoAuthenticationProvider`; never in any API response or log | `auth/SecurityConfig`, `users/UserService`, `users/UserDto` |
| Who may read what | The user ID comes from the session principal only; repository methods take it as a parameter (`findByIdAndUserId`), and a foreign record answers 404 like a missing one | `transactions/*`, `applications/*`, `OwnershipTest` |
| Sessions | `RCSESSION` cookie: HttpOnly, SameSite=Lax, Secure when `COOKIE_SECURE=true`; ID rotated on login; invalidated on logout; kept in the Java process (restart = logout) | `auth/SecurityConfig`, `auth/AuthController` |
| Cross-site requests | Double-submit CSRF cookie (`XSRF-TOKEN` / `X-XSRF-TOKEN`) on every state-changing call, login and logout included | `SecurityConfig`, `demo-repository/src/api/client.ts` |
| Data integrity | Foreign keys, `NUMERIC(12,2)` money, `CHECK` constraints on amounts, statuses, currency and normalized email, `UNIQUE` fixture IDs (idempotent seeding) | `db/migration/V1__initial_schema.sql`, `V2__…` |
| Query safety | JPA/Hibernate parameterised queries only; no string-built SQL | all repositories |
| Fixture values | Synthetic income/expenses/history are loaded from a versioned file; there is no HTTP route that writes them | `fixtures/DemoDataLoader`, `users/ProfileController` (read-only) |
| Database role | Backend connects as `rocket_app` (no SUPERUSER/CREATEDB/CREATEROLE); the superuser is used only by the Postgres image at init | `rocket-credit-deployment/diploma/db-init/01-app-role.sh` |
| Secrets | Passwords and the analysis token come from `diploma/.env` (git-ignored); `.env.example` holds placeholders only | `docker-compose.diploma.yml` |
| Analysis boundary | Python receives derived features only (no names, emails, IDs, hashes, cookies); it rejects unknown fields; the port is internal and needs `X-Analysis-Token` | `analysis/FeatureBundle`, `rocket-credit-analysis/app/models.py` |
| Logs | Request bodies are never logged at INFO; auth request records redact `toString()`; failure logs carry status codes, not payloads; the fixture dump/backups are outside Git | `application.yml`, `AuthRequests`, `AnalysisClient` |
| Third-party tokens | The Facebook access token is stored AES-256-GCM encrypted (random 12-byte IV; key derived from `SOCIAL_TOKEN_KEY`, else the Meta app secret) and never returned by the API. The OAuth `state` is 24 random bytes kept in the session and compared in constant time. A token without the `user_posts` permission is revoked and not stored; *Disconnect* deletes the row and revokes the app's permissions at Facebook | `signals/TokenCipher`, `SocialConnectionController`, `SocialConnectionService`, `SocialConnectionFlowTest` |
| Uploaded images | Address-evidence uploads: max 5 MB, PNG/JPEG checked by file signature, processed in a private volume (directory mode 700, non-root UID 10001), deleted after processing, never served from the web root | `address/AddressService`, `docker-compose.diploma.yml` |
| External AI (Gemini or Claude) | Off by default (`ANALYSIS_DEMO_PROVIDER_MODE=FIXTURE`); `AI_PROVIDER` picks Google Gemini or Anthropic Claude Haiku 5.5. In `AI` mode only repository-owned synthetic content (the prepared address card, synthetic cost sheet and sample posts) or the owner's *own* Facebook posts — when they press *Analyze posts* — are sent. The key lives only in the analysis container and travels in a header, not the URL. Prompts treat content as untrusted data; outputs are schema-checked and evidence IDs must exist, otherwise the result is discarded. Failures log only the error type or HTTP status | `rocket-credit-analysis/app/demo_analysis.py`, `tests/test_gemini.py`, `tests/test_anthropic.py` |
| Recoverability | `diploma/backup.sh` (pg_dump, gzip) and `diploma/restore.sh` (as the app role); demonstrated: delete 17 rows → restore → 46 rows, login works | `rocket-credit-deployment/diploma/` |

## Not protected by the application (documented limits)

- **Data at rest is not encrypted.** Password *hashing* protects credentials if the database file leaks; it does
  nothing for emails, histories or decisions. Those rely on protection of the host disk: the Docker volume
  `rocket-credit-diploma-dbdata` lives inside the Docker Desktop VM image on the laptop, so FileVault (or the
  equivalent OS disk encryption) is the control. Backups under `diploma/backups/` are plain SQL — keep them on
  an encrypted disk and delete them when no longer needed.
- **Transport is plain HTTP on localhost.** For a hosted demonstration put the backend behind HTTPS and set
  `COOKIE_SECURE=true`; otherwise the session cookie is readable on the network.
- **The database port is published on the host (5438)** for `psql` and backups. Nothing else should connect;
  remove the mapping for a shared machine.
- **Sessions are in memory**: no persistence, no revocation list beyond logout, no concurrent-session limit.
- **No rate limiting** on login. BCrypt's cost slows guessing, but a real deployment needs throttling.
- **Facebook tokens can be decrypted by anyone holding `diploma/.env`** (the key is derived from it). Protect that
  file like the database password.
- **In AI mode, connected Facebook posts leave the machine** and are processed by Google's Gemini API under Google's
  terms (or Anthropic's, with `AI_PROVIDER=ANTHROPIC`). Use `FIXTURE` mode to keep everything local.
- **CSRF token is the plain cookie value** (not XOR-masked): adequate for a same-origin demo; the masked
  handler would be the production choice.
