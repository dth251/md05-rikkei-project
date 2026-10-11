#!/usr/bin/env bash
set -euo pipefail
target="${1:-.env}"
[[ ! -e "$target" ]] || { echo 'Refusing to overwrite an existing secret file' >&2; exit 1; }
umask 077
set -o noclobber
{
    printf 'POSTGRES_USER=postgres\n'
    for variable in POSTGRES_PASSWORD AUTH_DB_PASSWORD PRODUCT_DB_PASSWORD ORDER_DB_PASSWORD PAYMENT_DB_PASSWORD NOTIFICATION_DB_PASSWORD RABBITMQ_DEFAULT_PASS; do
        printf '%s=%s\n' "$variable" "$(openssl rand -hex 32)"
    done
    printf 'RABBITMQ_DEFAULT_USER=commerce_app\n'
    printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 32)"
    printf 'DOMAIN=md05-rikkei-project.duckdns.org\n'
    printf 'AUTH_IMAGE=ghcr.io/dth251/md05-rikkei-project/auth-service\n'
} > "$target"
printf 'Created private environment file: %s\n' "$target"
