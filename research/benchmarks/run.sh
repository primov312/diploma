#!/usr/bin/env sh
# Sequential vs parallel feature preparation against the running diploma database.
# Usage (from the repository root or this folder):
#   research/benchmarks/run.sh                      # defaults: 20 warm-up, 200 iterations, delays 0,10,30 ms, pool 3
#   ITERATIONS=500 DELAYS=0,20,50 POOLS=2,3 OUT=results-pools.csv research/benchmarks/run.sh
# Requires: the diploma stack up (docker compose ... up), Java 21 + Maven on the host.
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
. "$ROOT/rocket-credit-deployment/diploma/.env"
cd "$ROOT/rocket-credit-backend"
DB_URL="jdbc:postgresql://localhost:${DB_HOST_PORT:-5438}/${DB_NAME:-rocketcredit}" \
DB_USER="${DB_APP_USER:-rocket_app}" DB_PASSWORD="${DB_APP_PASSWORD}" \
ANALYSIS_URL="http://localhost:1" ANALYSIS_SHARED_SECRET="unused" \
SPRING_PROFILES_ACTIVE=benchmark \
mvn -q -B spring-boot:run -Dspring-boot.run.arguments="\
--rocket.benchmark.iterations=${ITERATIONS:-200} \
--rocket.benchmark.warmup=${WARMUP:-20} \
--rocket.benchmark.delays-ms=${DELAYS:-0,10,30} \
--rocket.benchmark.pool-sizes=${POOLS:-3} \
--rocket.benchmark.out=$ROOT/research/benchmarks/${OUT:-results.csv}"
