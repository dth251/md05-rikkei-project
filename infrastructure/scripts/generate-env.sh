#!/usr/bin/env bash
set -euo pipefail
target="${1:-.env}"
[[ ! -e "$target" ]] || { echo 'Refusing to overwrite an existing secret file' >&2; exit 1; }
umask 077
set -o noclobber
lines=('POSTGRES_USER=postgres')
for variable in POSTGRES_PASSWORD AUTH_DB_PASSWORD PRODUCT_DB_PASSWORD ORDER_DB_PASSWORD PAYMENT_DB_PASSWORD NOTIFICATION_DB_PASSWORD RABBITMQ_DEFAULT_PASS PRODUCT_RPC_TOKEN; do
    value="$(openssl rand -hex 32)" || { echo 'Secret generation failed' >&2; exit 1; }
    [[ "$value" =~ ^[0-9a-f]{64}$ ]] || { echo 'Invalid generated secret' >&2; exit 1; }
    lines+=("$variable=$value")
done
value="$(openssl rand -base64 32)" || { echo 'JWT key generation failed' >&2; exit 1; }
[[ "$value" =~ ^[a-zA-Z0-9+/]{43}=$ ]] || { echo 'Invalid generated JWT key' >&2; exit 1; }
lines+=('RABBITMQ_DEFAULT_USER=commerce_app' "JWT_SECRET=$value"
    'DOMAIN=md05-rikkei-project.duckdns.org'
    'AUTH_IMAGE=ghcr.io/dth251/md05-rikkei-project/auth-service'
    'VNPAY_ENABLED=false' 'VNPAY_TMN_CODE=' 'VNPAY_HASH_SECRET='
    'VNPAY_RETURN_URL=https://md05-rikkei-project.duckdns.org/api/payments/vnpay/return')
printf '%s\n' "${lines[@]}" > "$target"
printf 'Created private environment file: %s\n' "$target"
