#!/usr/bin/env sh
# Dump the diploma database to diploma/backups/<timestamp>.sql.gz using pg_dump inside the db container.
# Usage: ./diploma/backup.sh            (run from rocket-credit-deployment/)
set -eu
cd "$(dirname "$0")/.."
. ./diploma/.env
OUT_DIR=diploma/backups
mkdir -p "$OUT_DIR"
STAMP=$(date +%Y%m%d-%H%M%S)
OUT="$OUT_DIR/rocketcredit-$STAMP.sql.gz"
docker compose -f docker-compose.diploma.yml --env-file diploma/.env exec -T db \
  pg_dump -U "${DB_ADMIN_USER:-rocket_admin}" -d "${DB_NAME:-rocketcredit}" --clean --if-exists --no-owner \
  | gzip > "$OUT"
echo "backup written: $OUT ($(du -h "$OUT" | cut -f1))"
