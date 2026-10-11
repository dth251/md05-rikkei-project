#!/usr/bin/env bash
set -euo pipefail
query() {
    docker compose exec -T postgres psql -U postgres -d postgres -Atc "$1"
}
for name in auth product order payment notification; do
    other=auth
    [[ "$name" != auth ]] || other=product
    result="$(query "SELECT has_database_privilege('${name}_user', '${name}_db', 'CONNECT') AND NOT has_database_privilege('${name}_user', '${other}_db', 'CONNECT')")"
    [[ "$result" == t ]] || { echo "Database isolation failed: $name" >&2; exit 1; }
done
count="$(query "SELECT count(*) FROM pg_database WHERE datname IN ('auth_db', 'product_db', 'order_db', 'payment_db', 'notification_db')")"
[[ "$count" == 5 ]]
queues="$(docker compose exec -T rabbitmq rabbitmqctl -q list_queues name durable)"
for queue in payment.order-created.q order.payment-result.q notification.order.q; do
    [[ "$queues" == *"$queue"$'\ttrue'* ]] || { echo "Missing durable queue: $queue" >&2; exit 1; }
done
bindings="$(docker compose exec -T rabbitmq rabbitmqctl -q list_bindings source_name destination_name routing_key)"
for key in order.created payment.succeeded payment.failed order.confirmed order.cancelled; do
    [[ "$bindings" == *"$key"* ]] || { echo "Missing binding: $key" >&2; exit 1; }
done
for service in postgres rabbitmq; do
    container="$(docker compose ps -q "$service")"
    ports="$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$container")"
    [[ "$ports" == '{}' || "$ports" == null ]] || { echo "Unexpected published DB/MQ port: $service" >&2; exit 1; }
done
echo 'PASS: five isolated databases/users, durable event topology and private ports.'
