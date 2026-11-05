#!/usr/bin/env bash
set -euo pipefail
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres --set=test_password="$DB_TEST_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE idiomas_test LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'test_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='idiomas_test') \gexec
ALTER ROLE idiomas_test LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'test_password';
SELECT 'CREATE DATABASE idiomas_test OWNER idiomas_test'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='idiomas_test') \gexec
SQL
