#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
test -f .env
if grep -qx 'SMTP_SANDBOX_ENABLED=true' .env; then
    for setting in SMTP_ENABLED=true SMTP_HOST=mailpit SMTP_PORT=1025 SMTP_AUTH=false SMTP_STARTTLS=false; do
        grep -qx "$setting" .env || { echo 'SMTP sandbox configuration is inconsistent' >&2; exit 1; }
    done
    docker compose -f docker-compose.yml -f docker-compose.production.yml -f docker-compose.smtp-test.yml --profile smtp-test \
        up -d --no-deps --wait --wait-timeout 180 mailpit
fi
if grep -qx 'MONITORING_ENABLED=true' .env; then
    bash infrastructure/scripts/observability-preflight.sh
    export MONITOR_DB_PASSWORD="$(sed -n 's/^MONITOR_DB_PASSWORD=//p' .env)"
    bash infrastructure/scripts/provision-monitoring.sh
    docker compose -f docker-compose.yml -f docker-compose.production.yml -f docker-compose.monitoring.yml --profile monitoring \
        up -d --no-deps --wait --wait-timeout 240 prometheus grafana node-exporter postgres-exporter redis-exporter
    echo 'PASS: monitoring services are ready; ports remain bound to loopback'
fi
