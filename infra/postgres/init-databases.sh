#!/bin/sh
# Runs once, when the postgres container starts with an empty data volume (docker-entrypoint-initdb.d).
# One Postgres process, four databases with four owners: each service only ever gets its own
# credentials, so the "database per service" rule from the laptop still holds on one small server.
set -eu

create_db() {
    db="$1"; user="$2"; password="$3"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE "$user" LOGIN PASSWORD '$password';
CREATE DATABASE "$db" OWNER "$user";
REVOKE ALL ON DATABASE "$db" FROM PUBLIC;
SQL
}

create_db "$IDENTITY_DB_NAME" "$IDENTITY_DB_USER" "$IDENTITY_DB_PASSWORD"
create_db "$PROFILE_DB_NAME"  "$PROFILE_DB_USER"  "$PROFILE_DB_PASSWORD"
create_db "$MEAL_DB_NAME"     "$MEAL_DB_USER"     "$MEAL_DB_PASSWORD"
create_db "$ANALYSIS_DB_NAME" "$ANALYSIS_DB_USER" "$ANALYSIS_DB_PASSWORD"
