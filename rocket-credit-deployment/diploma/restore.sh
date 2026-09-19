#!/usr/bin/env sh
# Restore a dump produced by backup.sh into the running db container.
# Usage: ./diploma/restore.sh diploma/backups/rocketcredit-<stamp>.sql.gz
# The dump contains DROP/CREATE statements; stop the backend first so no session holds a connection.
set -eu
cd "$(dirname "$0")/.."
[ $# -eq 1 ] || { echo "usage: $0 <backup.sql.gz>" >&2; exit 2; }
. ./diploma/.env
docker compose -f docker-compose.diploma.yml --env-file diploma/.env stop backend >/dev/null
# Restore as the application role so the recreated tables keep the same owner as before.
gunzip -c "$1" | docker compose -f docker-compose.diploma.yml --env-file diploma/.env exec -T \
  -e PGPASSWORD="${DB_APP_PASSWORD}" db \
  psql -v ON_ERROR_STOP=1 -q -U "${DB_APP_USER:-rocket_app}" -d "${DB_NAME:-rocketcredit}"
docker compose -f docker-compose.diploma.yml --env-file diploma/.env start backend >/dev/null
echo "restored $1"
