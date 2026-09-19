# Diploma runtime — start, stop, health

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

## Check

| Check | Command / URL | Expect |
| --- | --- | --- |
| Containers | `docker compose -f docker-compose.diploma.yml --env-file diploma/.env ps` | all `healthy` |
| Backend + analysis | `curl -s localhost:8080/api/health` | `{"backend":"ok","analysis":"ok","policyVersion":"rules-v1"}` |
| Detailed health | `curl -s localhost:8080/actuator/health` | `status: UP`, components `db` and `analysis` UP |
| Web app | http://localhost:8080 | React app |
| Analysis directly | not published; from inside: `docker compose ... exec backend curl -s http://analysis:8000/health` | `{"status":"ok",...}` |

The analysis service is only reachable on the Compose network and requires the `X-Analysis-Token`
header (value `ANALYSIS_SHARED_SECRET`) for `/policy` and `/score`; `/health` is open for the container healthcheck.

## Stop

```bash
docker compose -f docker-compose.diploma.yml --env-file diploma/.env down        # keeps the database volume
docker compose -f docker-compose.diploma.yml --env-file diploma/.env down -v     # also deletes rocket-credit-diploma-dbdata
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
