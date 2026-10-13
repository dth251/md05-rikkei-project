#!/usr/bin/env bash
set -euo pipefail
: "${MONITOR_DB_PASSWORD:?Load private MONITOR_DB_PASSWORD into the process}"
[[ "$MONITOR_DB_PASSWORD" =~ ^[0-9a-f]{64}$ ]] || { echo 'Monitoring password must be 64 lowercase hex characters' >&2; exit 1; }
# Password travels over stdin, not SQL command arguments or logs.
{
    printf 'BEGIN;\n'
    printf "DO \$\$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='monitor_user') THEN CREATE ROLE monitor_user LOGIN; END IF; END \$\$;\n"
    printf "ALTER ROLE monitor_user WITH LOGIN PASSWORD '%s';\n" "$MONITOR_DB_PASSWORD"
    printf 'GRANT pg_monitor TO monitor_user;\nGRANT CONNECT ON DATABASE postgres TO monitor_user;\nCOMMIT;\n'
} | docker compose exec -T postgres psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q -o /dev/null 2>/dev/null \
    || { echo 'Monitoring role provisioning failed; credentials withheld' >&2; exit 1; }
echo 'PASS: monitoring role owns no application database and has no application table grants'
