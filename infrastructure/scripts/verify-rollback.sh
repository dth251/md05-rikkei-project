#!/usr/bin/env bash
set -euo pipefail
# Exercise the real deployer with a known cached candidate and one forced smoke failure.
# Only Notification is recreated; no image, schema, secret, queue or volume is deleted.
root="${DEPLOY_ROOT:-/opt/omnichannel-ecommerce}"
candidate="${1:?Provide a cached known-good candidate SHA different from current Notification}"
[[ "$root" =~ ^/[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$ && "$candidate" =~ ^[0-9a-f]{40}$ ]]
release="${PROBE_RELEASE:-$(readlink -f "$root/current")}"
[[ "$release" == "$root/releases/"* && -f "$release/infrastructure/scripts/deploy.sh" ]]
previous="$(sed -n 's/^NOTIFICATION_IMAGE_TAG=//p' "$root/.image-tags")"
[[ "$previous" =~ ^[0-9a-f]{40}$ && "$candidate" != "$previous" ]]
repository=ghcr.io/dth251/md05-rikkei-project/notification-service
docker image inspect "$repository:$candidate" >/dev/null
docker image inspect "$repository:$previous" >/dev/null
before_manifest="$(sha256sum "$root/.image-tags")"
before_release="$(readlink "$root/current")"
probe="$(mktemp -d /tmp/omnichannel-rollback.XXXXXX)"
export PROBE_DOCKER="$(command -v docker)" PROBE_CURL="$(command -v curl)" PROBE_CANDIDATE="$repository:$candidate" PROBE_MARKER="$probe/force-smoke-failure"
touch "$PROBE_MARKER"
cleanup() {
    rm -f "$probe/docker" "$probe/curl" "$PROBE_MARKER"
    rmdir "$probe"
}
trap cleanup EXIT
cat > "$probe/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
if [[ " $* " == *' pull notification-service '* ]]; then
    # The probe verifies rollback, not registry access; reuse this already-tested image.
    "$PROBE_DOCKER" image inspect "$PROBE_CANDIDATE" >/dev/null
    echo 'Rollback probe: reusing cached candidate image.'
    exit 0
fi
exec "$PROBE_DOCKER" "$@"
DOCKER
cat > "$probe/curl" <<'CURL'
#!/usr/bin/env bash
set -euo pipefail
if [[ " $* " == *' http://127.0.0.1:8084/actuator/health '* && -f "$PROBE_MARKER" ]]; then
    rm -f "$PROBE_MARKER"
    echo 'Rollback probe: forced candidate smoke failure.' >&2
    exit 22
fi
exec "$PROBE_CURL" "$@"
CURL
chmod 700 "$probe/docker" "$probe/curl"
date -u '+%Y-%m-%dT%H:%M:%SZ'
set +e
PATH="$probe:$PATH" IMAGE_TAG="$candidate" DEPLOY_SERVICE=notification-service DEPLOY_ROOT="$root" \
    bash "$release/infrastructure/scripts/deploy.sh"
result=$?
set -e
[[ "$result" != 0 && ! -f "$PROBE_MARKER" ]]
actual="$(docker inspect --format '{{.Config.Image}}' omnichannel-day1-notification-service-1)"
[[ "$actual" == "$repository:$previous" ]]
[[ "$(sha256sum "$root/.image-tags")" == "$before_manifest" && "$(readlink "$root/current")" == "$before_release" ]]
curl -fsS --max-time 15 http://127.0.0.1:8084/actuator/health | jq -e '.status == "UP"' >/dev/null
date -u '+%Y-%m-%dT%H:%M:%SZ'
echo "PASS: candidate $candidate rejected; Notification restored to $previous; manifest/current unchanged."
