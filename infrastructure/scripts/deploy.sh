#!/usr/bin/env bash
set -euo pipefail
release="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$release"
: "${IMAGE_TAG:?Set tested commit SHA}"
: "${DEPLOY_ROOT:?Set deployment root}"
[[ "$IMAGE_TAG" =~ ^[0-9a-f]{40}$ ]]
[[ "$DEPLOY_ROOT" =~ ^/[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$ ]]
[[ -f .env ]] || { echo 'VPS .env is missing' >&2; exit 1; }
service="${DEPLOY_SERVICE:-auth-service}"
case "$service" in
    auth-service) tag_variable=AUTH_IMAGE_TAG; port=8081 ;;
    api-gateway) tag_variable=GATEWAY_IMAGE_TAG; port=8080 ;;
    product-service) tag_variable=PRODUCT_IMAGE_TAG; port=8082 ;;
    order-service) tag_variable=ORDER_IMAGE_TAG; port=8083 ;;
    payment-service) tag_variable=PAYMENT_IMAGE_TAG; port=8085 ;;
    notification-service) tag_variable=NOTIFICATION_IMAGE_TAG; port=8084 ;;
    *) echo 'Unsupported deployment service' >&2; exit 1 ;;
esac
exec 9>"$DEPLOY_ROOT/.deploy.lock"
flock -w 300 9
tags_file="$DEPLOY_ROOT/.image-tags"
if [[ -f "$tags_file" ]]; then
    while IFS='=' read -r name value; do
        [[ "$name" =~ ^(AUTH|GATEWAY|PRODUCT|ORDER|PAYMENT|NOTIFICATION)_IMAGE_TAG$ && "$value" =~ ^[0-9a-f]{40}$ ]] \
            || { echo 'Invalid deployed image manifest' >&2; exit 1; }
        export "$name=$value"
    done < "$tags_file"
elif [[ -f "$DEPLOY_ROOT/.current-image-tag" ]]; then
    value="$(cat "$DEPLOY_ROOT/.current-image-tag")"
    [[ "$value" =~ ^[0-9a-f]{40}$ ]]
    export AUTH_IMAGE_TAG="$value"
fi
previous="${!tag_variable:-}"
export "$tag_variable=$IMAGE_TAG"
mkdir -p "$DEPLOY_ROOT/infrastructure/postgres" "$DEPLOY_ROOT/infrastructure/rabbitmq"
cp infrastructure/postgres/init-databases.sh "$DEPLOY_ROOT/infrastructure/postgres/"
cp infrastructure/rabbitmq/definitions.json "$DEPLOY_ROOT/infrastructure/rabbitmq/"
compose=(docker compose --project-directory "$DEPLOY_ROOT" --env-file "$DEPLOY_ROOT/.env" -f "$release/docker-compose.yml" -f "$release/docker-compose.production.yml")
"${compose[@]}" config --quiet
"${compose[@]}" pull "$service"
"${compose[@]}" up -d --wait --wait-timeout 180 postgres rabbitmq
"${compose[@]}" run --rm --no-deps rabbitmq-topology
bash infrastructure/scripts/verify-infrastructure.sh
bash infrastructure/scripts/apply-schema.sh "$service"
if [[ "$service" == api-gateway ]]; then
    "${compose[@]}" up -d --wait --wait-timeout 120 redis
fi
verify_service() {
    curl --fail --silent --show-error --max-time 15 "http://127.0.0.1:$port/actuator/health" \
        | jq -e '.status == "UP"' > /dev/null || return 1
    if [[ "$service" == auth-service || "$service" == api-gateway ]]; then
        bash infrastructure/scripts/smoke-auth.sh "http://127.0.0.1:$port" || return 1
    elif [[ "$service" == product-service ]]; then
        curl --fail --silent --show-error --max-time 15 "http://127.0.0.1:$port/api/products" \
            | jq -e '.success == true' > /dev/null || return 1
    fi
}
if "${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 240 "$service" \
        && verify_service; then
    umask 077
    {
        for name in AUTH_IMAGE_TAG GATEWAY_IMAGE_TAG PRODUCT_IMAGE_TAG ORDER_IMAGE_TAG PAYMENT_IMAGE_TAG NOTIFICATION_IMAGE_TAG; do
            value="${!name:-}"
            [[ -z "$value" ]] || printf '%s=%s\n' "$name" "$value"
        done
    } > "$tags_file.next"
    mv "$tags_file.next" "$tags_file"
    if [[ "$service" == auth-service ]]; then
        printf '%s\n' "$IMAGE_TAG" > "$DEPLOY_ROOT/.current-image-tag"
    fi
    ln -sfn "$release" "$DEPLOY_ROOT/current"
    "${compose[@]}" ps
    printf '%s deployed and verified at commit %s\n' "$service" "$IMAGE_TAG"
else
    if [[ "$previous" =~ ^[0-9a-f]{40}$ ]]; then
        export "$tag_variable=$previous"
        "${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 240 "$service"
        verify_service
        echo "Rolled $service back to previous tested image: $previous" >&2
    fi
    exit 1
fi
