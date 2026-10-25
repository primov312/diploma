# Diploma runtime — start, stop, health

> Payment scope update: the application now supports simulated balances, purchases, interest-bearing credit schedules and repayments. See [Balance and repayments](../docs/BALANCE_AND_REPAYMENTS.md) for the current behavior; earlier exclusions of simulated payments below are historical. Real payment providers remain outside scope.

Three containers: `backend` (Java, serves the React build), `analysis` (Python, stateless) and `db` (PostgreSQL 16).
No Redis, MinIO, Kafka or the old Gateway/User Data/Payment/Repayment services.

## First time

```bash
cd rocket-credit-deployment
cp diploma/.env.example diploma/.env     # then change every password/secret in diploma/.env
```

`diploma/.env` is git-ignored. The database gets two roles: `rocket_admin` (superuser, used only by the
Postgres image itself) and `rocket_app` (no superuser/createdb/createrole; owns the `public` schema).
The backend connects as `rocket_app`.

## Start

```bash
docker compose -f docker-compose.diploma.yml --env-file diploma/.env up --build -d
```

## Load the Budapest price data (once per new database)

The location benchmarks and monthly living costs come from the researched snapshots in `scripts/data/`. After the
first start of a **new** database volume (the backend must have run its migrations), load them once:

```bash
./diploma/import-prices.sh        # creates scripts/.venv on first use; re-running is a no-op
```

Without this step the app still works, but the Budget tab says “Price data is not loaded on this server yet” and
visit prices are missing.

## Check

| Check | Command / URL | Expect |
| --- | --- | --- |
| Containers | `docker compose -f docker-compose.diploma.yml --env-file diploma/.env ps` | all `healthy` |
| Backend + analysis | `curl -s localhost:8080/api/health` | `{"backend":"ok","analysis":"ok","policyVersion":"rules-v4"}` |
| Detailed health | `curl -s localhost:8080/actuator/health` | `status: UP`, components `db` and `analysis` UP |
| Web app | http://localhost:8080 | React app |
| Analysis directly | not published; from inside: `docker compose ... exec backend curl -s http://analysis:8000/health` | `{"status":"ok",...}` |

The analysis service is only reachable on the Compose network and requires the `X-Analysis-Token`
header (value `ANALYSIS_SHARED_SECRET`) for `/policy` and `/score`; `/health` is open for the container healthcheck.

The diploma Compose file activates `app/policy-v4.json` so estimates and new application decisions share
`affordability-v4`, including automatic verified-address budgets and location spending forecasts. Set any existing
`ANALYSIS_POLICY_PATH` override in `diploma/.env` to the same path. Legacy `app/policy-v3.json`, `app/policy-v2.json` and
`app/policy.json` remain available for deliberate policy rollback. Flyway migrations preserve prior
application snapshots and decisions. See [monthly pricing setup](../docs/LOCATION_PRICING.md#monthly-living-costs-and-postal-code-lookup) for the manual V11 dataset import and V12 automatic-budget rollout.

Demo analysis defaults to `ANALYSIS_DEMO_PROVIDER_MODE=FIXTURE`. For live model extraction, set
`ANALYSIS_DEMO_PROVIDER_MODE=AI` and the key of the chosen `AI_PROVIDER` in the ignored `.env`; the key is passed only to the
private analysis container. AI mode accepts repository-owned synthetic address/cost evidence. For social analysis it also accepts posts from the account owner's own connected Facebook profile (see `docs/SOCIAL_ACCOUNT_ANALYSIS.md`); those posts are sent to Gemini only when the user runs that analysis in AI mode.
Model, timeout, prompt version, source passages, and evidence IDs are included in returned reports. Without
a key or on provider failure, estimates remain available and reports say `AI_UNAVAILABLE`.

Address image uploads are limited to 5 MB and checked against PNG/JPEG file signatures. Processing copies
are stored in the private `rocket-credit-diploma-address-evidence` volume and removed after processing; no
image is served from the static web root. Only the prepared synthetic card is sent to the AI provider. Other images
receive `NEEDS_REVIEW` in this demo build. Monthly district rent and grocery references use the researched
HUF snapshot described in `docs/LOCATION_PRICING.md`. Legacy salary comparison references remain synthetic
USD data.

The runtime image creates the evidence directory for backend UID 10001 with mode 700. For an existing
volume created by an older image, repair its ownership once after starting the backend:

```bash
docker compose -f docker-compose.diploma.yml --env-file diploma/.env exec --user root backend \
  sh -c 'chown backend:backend /var/lib/rocket-credit/address-evidence && chmod 700 /var/lib/rocket-credit/address-evidence'
```

## Stop

```bash
docker compose -f docker-compose.diploma.yml --env-file diploma/.env down        # keeps the database volume
docker compose -f docker-compose.diploma.yml --env-file diploma/.env down -v     # deletes both database and address-evidence volumes
```

Sessions live in the Java process: restarting `backend` logs everyone out.

## Development without Docker

```bash
# 1) database (any local PostgreSQL 16; create the app role like diploma/db-init/01-app-role.sh does)
# 2) analysis
cd rocket-credit-analysis && . .venv/bin/activate && ANALYSIS_SHARED_SECRET=dev uvicorn app.main:app --port 8000 --reload
# 3) backend
cd rocket-credit-backend && DB_URL=jdbc:postgresql://localhost:5438/rocketcredit DB_USER=rocket_app DB_PASSWORD=... \
  ANALYSIS_SHARED_SECRET=dev mvn spring-boot:run
# 4) frontend with API proxy to :8080
cd demo-repository && npm run dev
```

## Turning on AI mode (Jev, Gemini or Claude) and testing it

AI mode calls TypeSafe's Jev by default (`AI_PROVIDER=JEV`), or Google Gemini or Anthropic's Claude Haiku 5.5
(`claude-haiku-5-5`, the cheapest Claude model, about $0.10 / $0.50 per million input / output tokens). Gemini and
Claude run the same prompts, schemas and checks. Jev returns typed answers only, so its adapters ask narrow
questions and code writes the results (see the thesis, section 7.2).

**Jev:** in `diploma/.env` set `ANALYSIS_DEMO_PROVIDER_MODE=AI`, `AI_PROVIDER=JEV` and `TYPESAFE_API_KEY=<key from
console.typesafe.ai>`; the model is pinned to `TYPESAFE_MODEL=jev-1.13.0`. Then run `docker compose ... up -d --build analysis`
(the image needs the new `typesafe-sdk` dependency). Live test:

```bash
cd rocket-credit-analysis
set -a; . ../rocket-credit-deployment/diploma/.env; set +a
ANALYSIS_TYPESAFE_API_KEY=$TYPESAFE_API_KEY .venv/bin/python -m pytest tests/test_jev.py -k live -v
```

The address card is an image, which TypeSafe offers as a preview; the adapter sends the required
`TypeSafe-Danger-Zone-Images: true` header for that request only.

**Claude:** in `diploma/.env` set `ANALYSIS_DEMO_PROVIDER_MODE=AI`, `AI_PROVIDER=ANTHROPIC` and `ANTHROPIC_API_KEY=<key from
console.anthropic.com>` (the account needs credits), then `docker compose ... up -d analysis`. Live test:

```bash
cd rocket-credit-analysis
set -a; . ../rocket-credit-deployment/diploma/.env; set +a
ANALYSIS_ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY .venv/bin/python -m pytest tests/test_anthropic.py -k live -v
```

**Gemini:**


1. In `diploma/.env` set `ANALYSIS_DEMO_PROVIDER_MODE=AI`, `GEMINI_API_KEY=<your key>` and a model your key can use
   (`GEMINI_MODEL=gemini-3.8-flash`; on 2026-10-22 Google no longer offered `gemini-2.5-flash` to new keys), then restart:
   `docker compose ... up -d analysis`. The Google AI Studio project behind the key needs credits or billing:
   HTTP 402 `RESOURCE_EXHAUSTED` (“prepayment credits are depleted”) means the key is valid but the project cannot pay.
2. `docker compose ... exec backend curl -s http://analysis:8000/health` shows `"geminiConfigured": true`.
3. Live test of all three Gemini features (social posts, local-cost extraction, address card) from the host:

```bash
cd rocket-credit-analysis
set -a; . ../rocket-credit-deployment/diploma/.env; set +a
ANALYSIS_GEMINI_API_KEY=$GEMINI_API_KEY ANALYSIS_GEMINI_MODEL=$GEMINI_MODEL .venv/bin/python -m pytest tests/test_gemini.py -k live -v
```

Without a key that test is skipped; the mocked Gemini tests in the same file always run. In the app, a successful
AI report shows the badge “Gemini · <model>”; a failed call shows “AI analysis is unavailable right now” and the
analysis logs an `ai call failed` line with the provider, model and HTTP status or stop reason (never the content).

## Facebook login

Register `http://localhost:8080/api/me/social-connection/facebook/callback` (Compose) and/or
`http://localhost:5173/api/me/social-connection/facebook/callback` (Vite) as *Valid OAuth Redirect URIs* in the Meta
app, and set `META_APP_ID`/`META_APP_SECRET` in `diploma/.env`. Details: [docs/SOCIAL_ACCOUNT_ANALYSIS.md](../docs/SOCIAL_ACCOUNT_ANALYSIS.md).

## Fresh-start verification (2026-10-22)

The stack was started from scratch under a separate Compose project with new volumes and other host ports, so the
regular `rocket-credit-diploma-*` volumes were not touched:

```bash
docker compose -p rocket-credit-fresh-check -f docker-compose.diploma.yml -f fresh-override.yml --env-file diploma/.env up --build -d
DB_HOST_PORT=5440 ./diploma/import-prices.sh
```

(`fresh-override.yml` only remapped ports to 5440/8081 and renamed the two volumes.) Result: images built in about
1.5 minutes, all three containers healthy, `/api/health` = `rules-v4`, both price snapshots imported, the persona
matrix in [docs/DEMO_SCENARIOS.md](../docs/DEMO_SCENARIOS.md) reproduced, and all 18 Playwright browser tests passed
against the production build. Shutdown with `down -v` removed the throwaway volumes.

## Legacy

The original five-service checkout stack (Gateway, User Data, Payment, Repayment, Redis, MinIO, four
databases) was removed from the repository in the cleanup after Step 7; it is available in git history up to
commit `1b886c0`. Its Docker images and volumes (`userdb`, `creditdb`, `paymentdb`, `repaymentdb`,
`rocket-credit-deployment_redisdata`) may still exist on the machine; the diploma stack does not use them
and uses its own volume `rocket-credit-diploma-dbdata` and host port 5438.

## Backup and restore

```bash
./diploma/backup.sh                                   # -> diploma/backups/rocketcredit-<stamp>.sql.gz
./diploma/restore.sh diploma/backups/rocketcredit-<stamp>.sql.gz
```

`backup.sh` runs `pg_dump --clean` inside the `db` container; `restore.sh` stops the backend, replays the
dump as the application role (so table ownership is unchanged) and starts the backend again. Backups are
git-ignored and unencrypted — see [docs/SAFE_DATA_KEEPING.md](../docs/SAFE_DATA_KEEPING.md).

## Demo accounts

Seeded at every backend start (idempotent). Password for all: `rocket-demo-123`. Details and expected
outcomes: [docs/DEMO_SCENARIOS.md](../docs/DEMO_SCENARIOS.md).
