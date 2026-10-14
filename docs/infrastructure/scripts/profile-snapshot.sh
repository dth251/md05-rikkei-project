#!/usr/bin/env bash
set -euo pipefail
# Sanitized measurements only. No container inspect environment, log bodies or DB payload.
date -u '+%Y-%m-%dT%H:%M:%SZ'
df -Pk .
free -m
docker stats --no-stream --format '{{.Name}} CPU={{.CPUPerc}} RAM={{.MemUsage}}' \
    $(docker ps -q --filter label=com.docker.compose.project=omnichannel-day1)
docker compose exec -T rabbitmq rabbitmqctl -q list_queues name messages_ready messages_unacknowledged consumers </dev/null
for database in order_db payment_db; do
    printf '%s backlog/age/query plan:\n' "$database"
    docker compose exec -T postgres psql -U postgres -d "$database" -v ON_ERROR_STOP=1 -c \
        'SELECT count(*) AS pending,extract(epoch FROM CURRENT_TIMESTAMP-min(created_at)) AS oldest_seconds FROM outbox_events WHERE published_at IS NULL' </dev/null
    docker compose exec -T postgres psql -U postgres -d "$database" -v ON_ERROR_STOP=1 -c \
        'EXPLAIN (ANALYZE,BUFFERS) SELECT event_id FROM outbox_events WHERE published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP) ORDER BY created_at,event_id LIMIT 50' </dev/null
done
for port in 8080 8081 8082 8083 8084 8085; do
    curl -fsS --max-time 10 "http://127.0.0.1:$port/actuator/prometheus" \
        | grep -E '^(jvm_(memory|gc|threads)|hikaricp_|commerce_(outbox|backlog|order_product_rpc|product_rpc))' || true
done
