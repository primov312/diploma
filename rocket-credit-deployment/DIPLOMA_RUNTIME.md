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
`ANALYSIS_DEMO_PROVIDER_MODE=AI` and `TYPESAFE_API_KEY` in the ignored `.env`; the key is passed only to the
private analysis container. In AI mode every uploaded address or housing document is sent to Jev, together with the
account name and saved address it must show. For social analysis it also accepts posts from the account owner's own connected Facebook profile (see `docs/SOCIAL_ACCOUNT_ANALYSIS.md`); those posts are sent to Jev only when the user runs that analysis in AI mode.
Model, timeout, prompt version, source passages, and evidence IDs are included in returned reports. Without
a key or on provider failure, estimates remain available and reports say `AI_UNAVAILABLE`.

Address image uploads are limited to 5 MB and checked against PNG/JPEG file signatures. Processing copies
are stored in the private `rocket-credit-diploma-address-evidence` volume and removed after processing; no
image is served from the static web root. In AI mode any PNG or JPEG is sent to Jev, which checks that it shows the
account name, postal code, street and building (and the saved flat); the postal code also fixes the district. In
fixture mode only the prepared synthetic samples are checked, and other images receive `NEEDS_REVIEW`. Monthly district rent and grocery references use the researched
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

## Turning on AI mode (Jev) and testing it

Jev (TypeSafe System One) is the only AI model. It returns typed answers only (yes/no probabilities, choices
and scores), so each analysis asks narrow questions and code writes every value and sentence (see the thesis,
section 7.2). In `diploma/.env` set `ANALYSIS_DEMO_PROVIDER_MODE=AI` and `TYPESAFE_API_KEY=<key from
console.typesafe.ai>`; the model is pinned to `TYPESAFE_MODEL=jev-1.13.0`. Then run `docker compose ... up -d --build analysis`.
`docker compose ... exec backend curl -s http://analysis:8000/health` shows `"aiConfigured": true`. Live test:

```bash
cd rocket-credit-analysis
export ANALYSIS_TYPESAFE_API_KEY="$(grep '^TYPESAFE_API_KEY=' ../rocket-credit-deployment/diploma/.env | cut -d= -f2-)"
.venv/bin/python -m pytest tests/test_jev_live.py -v
```

Without a key those tests are skipped; the mocked tests in `tests/test_*.py` always run. Documents are images,
which TypeSafe offers as a preview; the document checks send the required `TypeSafe-Danger-Zone-Images: true`
header for those requests only. In the app, a successful AI report shows the badge “Jev · <model>”; a failed call
shows “AI analysis is unavailable right now” and the analysis logs an `ai call failed` line with the model and
HTTP status or error type (never the content).

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
