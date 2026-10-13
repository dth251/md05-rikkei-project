#!/usr/bin/env bash
set -euo pipefail
: "${DEPLOY_ROOT:?Set deployment root}"
: "${DEMO_ACTION_SCRIPT:?Set absolute private executable fixture/action script}"
dependency="${1:-notification-service}"
[[ "$dependency" =~ ^(notification-service|payment-service|rabbitmq)$ ]]
[[ "$DEPLOY_ROOT" =~ ^/[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$ ]]
[[ "$DEMO_ACTION_SCRIPT" == /* && -x "$DEMO_ACTION_SCRIPT" ]]
cd "$DEPLOY_ROOT/current"
bash infrastructure/scripts/observability-preflight.sh
exec 9>"$DEPLOY_ROOT/.deploy.lock"
flock -n 9 || { echo 'Deployment/demo already running' >&2; exit 1; }
# Select the existing running container by exact Compose ownership. Start that ID in finally.
mapfile -t containers < <(docker ps -q --filter label=com.docker.compose.project=omnichannel-day1 \
    --filter "label=com.docker.compose.service=$dependency")
(( ${#containers[@]} == 1 )) || { echo 'Expected one running project dependency' >&2; exit 1; }
container="${containers[0]}"
restore() {
    result=$?
    trap - EXIT INT TERM
    docker start "$container" >/dev/null || { echo 'Dependency restoration failed' >&2; exit 1; }
    "$DEMO_ACTION_SCRIPT" restored || result=1
    bash infrastructure/scripts/profile-snapshot.sh || result=1
    printf 'Demo end UTC=%s result=%s\n' "$(date -u +%FT%TZ)" "$result"
    exit "$result"
}
printf 'Demo start UTC=%s dependency=%s\n' "$(date -u +%FT%TZ)" "$dependency"
docker inspect --format '{{.Name}} image={{.Config.Image}} image_id={{.Image}}' "$container"
bash infrastructure/scripts/profile-snapshot.sh
"$DEMO_ACTION_SCRIPT" prepare
trap restore EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
docker stop --time 20 "$container" >/dev/null
"$DEMO_ACTION_SCRIPT" unavailable
# A failed action exits through the same restoration path. No purge/reset/recreate.
