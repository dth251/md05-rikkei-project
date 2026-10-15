#!/usr/bin/env bash
set -euo pipefail
source_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_ROOT="${FRONTEND_ROOT:-/var/www/md05-frontend}"
: "${FRONTEND_REVISION:?Set the tested full Git SHA}"
[[ "$FRONTEND_ROOT" =~ ^/[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$ ]]
[[ "$FRONTEND_REVISION" =~ ^[0-9a-f]{40}$ ]]
[[ -f "$source_root/dist/index.html" && -d "$source_root/dist/assets" ]]
target="$FRONTEND_ROOT/releases/$FRONTEND_REVISION"
mkdir -p "$target"
cp -R "$source_root/dist/." "$target/"
find "$target" -type d -exec chmod 755 {} +
find "$target" -type f -exec chmod 644 {} +
ln -sfn "$target" "$FRONTEND_ROOT/current.next"
mv -Tf "$FRONTEND_ROOT/current.next" "$FRONTEND_ROOT/current"
printf 'Frontend release activated: %s\n' "$FRONTEND_REVISION"
