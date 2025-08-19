#!/usr/bin/env sh
set -e

# 1) Wait for DB (pure-Python probe; no psql needed)
echo "Waiting for database at ${SQLALCHEMY_URL} ..."
for i in $(seq 1 30); do
  python - <<'PY' && break || true
import os, sys, time
from sqlalchemy import create_engine
url = os.environ.get("SQLALCHEMY_URL","")
if not url: 
    print("SQLALCHEMY_URL not set", file=sys.stderr); sys.exit(1)
try:
    eng = create_engine(url, future=True)
    with eng.connect() as c: pass
    print("DB reachable")
except Exception as e:
    print(f"DB not ready: {e}", file=sys.stderr); sys.exit(2)
PY
  echo "retrying DB in 2s..."; sleep 2
done

# 2) Run migrations
echo "Running Alembic migrations..."
alembic upgrade head

# 3) Start app
echo "Starting Uvicorn..."
exec uvicorn app.main:app --host 0.0.0.0 --port "${PORT:-8082}" --workers "${WORKERS:-4}"