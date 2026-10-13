#!/usr/bin/env bash
set -euo pipefail
[[ $# -le 1 ]] || { echo 'Use no arguments or --allow-loopback for local tests' >&2; exit 1; }
port_mode="${1:-private}"
[[ "$port_mode" == private || "$port_mode" == --allow-loopback ]] \
    || { echo 'Unsupported port verification mode' >&2; exit 1; }
query() {
    docker compose exec -T postgres psql -U postgres -d postgres -Atc "$1"
}
for name in auth product order payment notification; do
    result="$(query "SELECT pg_get_userbyid(datdba) = '${name}_user' AND has_database_privilege('${name}_user', '${name}_db', 'CONNECT') FROM pg_database WHERE datname = '${name}_db'")"
    [[ "$result" == t ]] || { echo "Database isolation failed: $name" >&2; exit 1; }
    for other in auth product order payment notification; do
        [[ "$name" != "$other" ]] || continue
        result="$(query "SELECT NOT has_database_privilege('${name}_user', '${other}_db', 'CONNECT')")"
        [[ "$result" == t ]] || { echo "Cross-database access detected: $name -> $other" >&2; exit 1; }
    done
done
count="$(query "SELECT count(*) FROM pg_database WHERE datname IN ('auth_db', 'product_db', 'order_db', 'payment_db', 'notification_db')")"
[[ "$count" == 5 ]]
queues="$(docker compose exec -T rabbitmq rabbitmqctl -q list_queues name durable)"
for queue in payment.order-created.q.dlq order.payment-result.q.dlq notification.order.q.dlq payment.order-expired.q.dlq notification.fulfillment.q.dlq payment.cod-lifecycle.q.dlq; do
    grep -Fqx "$queue"$'\ttrue' <<< "$queues" \
        || { echo "Missing durable dead letter queue: $queue" >&2; exit 1; }
done
policies="$(docker compose exec -T rabbitmq rabbitmqctl -q list_policies)"
for queue in payment.order-created.q order.payment-result.q notification.order.q payment.order-expired.q notification.fulfillment.q payment.cod-lifecycle.q; do
    grep -Fq "dlx-$queue" <<< "$policies" \
        || { echo "Missing scoped DLX policy: $queue" >&2; exit 1; }
done
for queue in payment.order-created.q order.payment-result.q notification.order.q payment.order-expired.q notification.fulfillment.q payment.cod-lifecycle.q; do
    grep -Fqx "$queue"$'\ttrue' <<< "$queues" || { echo "Missing durable queue: $queue" >&2; exit 1; }
done
bindings="$(docker compose exec -T rabbitmq rabbitmqctl -q list_bindings source_name destination_name routing_key)"
for binding in 'payment.order-created.q:order.created' 'order.payment-result.q:payment.succeeded' 'order.payment-result.q:payment.failed' 'notification.order.q:order.confirmed' 'notification.order.q:order.cancelled' 'payment.order-expired.q:order.expired' 'payment.order-expired.q:order.refund-requested' 'notification.fulfillment.q:order.shipped' 'notification.fulfillment.q:order.completed' 'payment.order-created.q:order.cod-created' 'payment.cod-lifecycle.q:order.cod-shipped' 'payment.cod-lifecycle.q:order.cod-cancelled'; do
    queue="${binding%%:*}"
    key="${binding#*:}"
    grep -Fqx 'commerce.events'$'\t'"$queue"$'\t'"$key" <<< "$bindings" \
        || { echo "Missing expected binding: $queue / $key" >&2; exit 1; }
done
for service in postgres rabbitmq; do
    container="$(docker compose ps -q "$service")"
    ports="$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$container")"
    if [[ "$port_mode" == private ]]; then
        [[ "$ports" == '{}' || "$ports" == null ]] || { echo "Unexpected published DB/MQ port: $service" >&2; exit 1; }
    else
        # Empty HostIp means every interface; emit the binding object so it cannot be mistaken for no bindings.
        addresses="$(docker inspect --format '{{range .HostConfig.PortBindings}}{{range .}}{{if .HostIp}}{{println .HostIp}}{{else}}{{println .}}{{end}}{{end}}{{end}}' "$container")"
        while IFS= read -r address; do
            [[ -z "$address" ]] && continue
            [[ "$address" == 127.0.0.1 || "$address" == ::1 ]] \
                || { echo "Non-loopback DB/MQ binding rejected: $service" >&2; exit 1; }
        done <<< "$addresses"
    fi
done
echo "PASS: five isolated databases/users, durable event topology and ports ($port_mode)."
