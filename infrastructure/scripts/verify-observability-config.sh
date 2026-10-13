#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
docker run --rm --entrypoint /bin/promtool -v "$root/infrastructure/monitoring:/etc/prometheus:ro" \
    prom/prometheus:v3.15.0 check config /etc/prometheus/prometheus.yml
docker run --rm --entrypoint /bin/promtool -w /etc/prometheus -v "$root/infrastructure/monitoring:/etc/prometheus:ro" \
    prom/prometheus:v3.15.0 test rules alerts-test.yml
docker run --rm -v "$root/infrastructure/tracing/collector.yml:/etc/otel/config.yml:ro" \
    otel/opentelemetry-collector-contrib:0.162.0 validate --config=/etc/otel/config.yml
docker run --rm -v "$root/infrastructure/logging/config.alloy:/etc/alloy/config.alloy:ro" \
    grafana/alloy:v1.20.1 validate /etc/alloy/config.alloy
docker run --rm -v "$root/infrastructure/logging/loki.yml:/etc/loki/config.yml:ro" \
    grafana/loki:3.7.8 -config.file=/etc/loki/config.yml -verify-config=true
docker run --rm --entrypoint /bin/amtool -v "$root/infrastructure/monitoring/alertmanager.yml:/etc/alertmanager/config.yml:ro" \
    prom/alertmanager:v0.34.1 check-config /etc/alertmanager/config.yml
echo 'PASS: Prometheus rules/config, Collector privacy pipeline, Alloy and Loki validated'
