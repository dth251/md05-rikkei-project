#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# Services generate their own Java DTOs. Only their Java namespace may differ.
diff -u <(sed '/^option java_package = /d' "$root/product-service/src/main/proto/catalog.proto") \
    <(sed '/^option java_package = /d' "$root/order-service/src/main/proto/catalog.proto")
echo 'PASS: independent Product and Order proto wire contracts agree.'
