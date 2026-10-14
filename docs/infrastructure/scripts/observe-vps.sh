#!/usr/bin/env bash
set -euo pipefail
root="${DEPLOY_ROOT:-/opt/omnichannel-ecommerce}"
[[ "$root" =~ ^/[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$ ]]
cd "$root/current"
date -u '+%Y-%m-%dT%H:%M:%SZ'
free -m
uptime
docker stats --no-stream --format '{{.Name}} CPU={{.CPUPerc}} RAM={{.MemUsage}}' \
    $(docker ps -q --filter label=com.docker.compose.project=omnichannel-day1)
docker compose exec -T rabbitmq rabbitmqctl -q list_queues name messages_ready messages_unacknowledged consumers </dev/null
for database in order_db payment_db; do
    printf '%s pending_outbox=' "$database"
    docker compose exec -T postgres psql -U postgres -d "$database" -Atc \
        'SELECT count(*) FROM outbox_events WHERE published_at IS NULL' </dev/null
done
for port in 8080 8081 8082 8083 8084 8085; do
    printf 'health_%s=' "$port"
    curl -fsS --max-time 10 "http://127.0.0.1:$port/actuator/health"
    printf '\n'
done
