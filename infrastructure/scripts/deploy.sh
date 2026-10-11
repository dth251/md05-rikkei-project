#!/usr/bin/env bash
set -euo pipefail
release="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$release"
: "${IMAGE_TAG:?Set tested commit SHA}"
: "${DEPLOY_ROOT:?Set deployment root}"
[[ "$IMAGE_TAG" =~ ^[0-9a-f]{40}$ ]]
[[ "$DEPLOY_ROOT" =~ ^/[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$ ]]
[[ -f .env ]] || { echo 'VPS .env is missing' >&2; exit 1; }
exec 9>"$DEPLOY_ROOT/.deploy.lock"
flock -w 300 9
mkdir -p "$DEPLOY_ROOT/infrastructure/postgres" "$DEPLOY_ROOT/infrastructure/rabbitmq"
cp infrastructure/postgres/init-databases.sh "$DEPLOY_ROOT/infrastructure/postgres/"
cp infrastructure/rabbitmq/definitions.json "$DEPLOY_ROOT/infrastructure/rabbitmq/"
compose=(docker compose --project-directory "$DEPLOY_ROOT" --env-file "$DEPLOY_ROOT/.env" -f "$release/docker-compose.yml" -f "$release/docker-compose.production.yml")
"${compose[@]}" config --quiet
previous=''
[[ ! -f "$DEPLOY_ROOT/.current-image-tag" ]] || previous="$(cat "$DEPLOY_ROOT/.current-image-tag")"
"${compose[@]}" pull auth-service
"${compose[@]}" up -d --wait --wait-timeout 180 postgres rabbitmq
"${compose[@]}" run --rm --no-deps rabbitmq-topology
bash infrastructure/scripts/verify-infrastructure.sh
if "${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 180 auth-service \
        && bash infrastructure/scripts/smoke-auth.sh; then
    printf '%s\n' "$IMAGE_TAG" > "$DEPLOY_ROOT/.current-image-tag"
    ln -sfn "$release" "$DEPLOY_ROOT/current"
    "${compose[@]}" ps
    printf 'Auth deployed and verified at commit %s\n' "$IMAGE_TAG"
else
    if [[ "$previous" =~ ^[0-9a-f]{40}$ ]]; then
        IMAGE_TAG="$previous" "${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 180 auth-service
        echo "Rolled Auth back to previous tested image: $previous" >&2
    fi
    exit 1
fi
