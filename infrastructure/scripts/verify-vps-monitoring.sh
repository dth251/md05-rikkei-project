#!/usr/bin/env bash
set -euo pipefail
cd "${DEPLOY_ROOT:-/opt/omnichannel-ecommerce}/current"
query() {
    curl -fsS --max-time 10 --get --data-urlencode "query=$1" http://127.0.0.1:19090/api/v1/query
}
for attempt in {1..40}; do
    result="$(query 'up{job="services"}')"
    if jq -e '.status=="success" and (.data.result|length)==6 and all(.data.result[]; .value[1]=="1")' <<< "$result" >/dev/null; then
        break
    fi
    (( attempt < 40 )) || { echo 'Six service scrape targets did not become UP' >&2; exit 1; }
    sleep 2
done
query 'up{job=~"host|postgres|redis|rabbitmq|rabbitmq-queues"}' \
    | jq -e '(.data.result|length)==5 and all(.data.result[]; .value[1]=="1")' >/dev/null
password="$(sed -n 's/^GRAFANA_ADMIN_PASSWORD=//p' .env)"
[[ "$password" =~ ^[0-9a-f]{64}$ ]] || { echo 'Load the private Grafana configuration' >&2; exit 1; }
grafana() {
    printf 'user = "ops:%s"\n' "$password" | curl --config - -fsS --max-time 15 "http://127.0.0.1:13000$1"
}
grafana /api/health | jq -e '.database=="ok"' >/dev/null
grafana /api/datasources/uid/prometheus/health | jq -e '.status=="OK"' >/dev/null
grafana /api/dashboards/uid/commerce-overview \
    | jq -e '(.dashboard.panels|length)>=22 and any(.dashboard.panels[]; .title=="Scheduled reservation expirations")' >/dev/null
query 'sum by (application) (http_server_requests_seconds_count)' \
    | jq -e '(.data.result|length)==6' >/dev/null
query 'node_memory_MemAvailable_bytes' | jq -e '(.data.result|length)==1' >/dev/null
query 'commerce_backlog_snapshot_valid' \
    | jq -e '(.data.result|length)==2 and all(.data.result[]; .value[1]=="1")' >/dev/null
echo 'PASS: six service and five dependency targets UP; Grafana dashboard, Prometheus datasource and resource/business metrics verified'
