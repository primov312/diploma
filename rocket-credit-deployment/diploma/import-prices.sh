#!/usr/bin/env sh
# Load the researched Budapest price snapshots (location benchmarks, then monthly living costs) into the
# diploma database. Needed once per new database volume, after the backend has applied its migrations.
# Re-running with the same snapshots is a no-op (the importers keep the existing version).
# Usage: ./diploma/import-prices.sh          (run from rocket-credit-deployment/)
#        DB_HOST_PORT=5440 ./diploma/import-prices.sh   (another stack on another port)
set -eu
cd "$(dirname "$0")/.."
PORT_OVERRIDE=${DB_HOST_PORT:-}
. ./diploma/.env
PORT=${PORT_OVERRIDE:-${DB_HOST_PORT:-5438}}
ROOT=..
VENV=$ROOT/scripts/.venv
if [ ! -x "$VENV/bin/python" ]; then
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install -q -r "$ROOT/scripts/requirements-location-pricing.txt"
fi
export LOCATION_PRICING_DATABASE_URL="postgresql://${DB_APP_USER:-rocket_app}:${DB_APP_PASSWORD}@localhost:${PORT}/${DB_NAME:-rocketcredit}"
"$VENV/bin/python" "$ROOT/scripts/import_budapest_location_prices.py" import \
  --input "$ROOT/scripts/data/budapest-location-prices-2026-09-30.json"
"$VENV/bin/python" "$ROOT/scripts/import_monthly_living_costs.py" import \
  --input "$ROOT/scripts/data/budapest-monthly-living-costs-2026-10-03.json"
echo "price snapshots imported into localhost:${PORT}"
