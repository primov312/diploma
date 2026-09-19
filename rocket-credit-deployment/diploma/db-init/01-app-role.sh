#!/bin/sh
# Runs once when the diploma database volume is first created.
# Creates a restricted application role: the backend never connects as the superuser.
set -eu
: "${APP_DB_USER:?APP_DB_USER is required}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE "${APP_DB_USER}" LOGIN PASSWORD '${APP_DB_PASSWORD}'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
-- Flyway needs to create tables in the public schema; the app owns that schema and nothing else.
GRANT CONNECT ON DATABASE "${POSTGRES_DB}" TO "${APP_DB_USER}";
ALTER SCHEMA public OWNER TO "${APP_DB_USER}";
SQL
