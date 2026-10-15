#!/usr/bin/env bash
set -euo pipefail
source_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
DOCS_ROOT="${DOCS_ROOT:-/var/www/md05-api-docs}"
: "${DOCS_REVISION:?Set the tested full Git SHA}"
[[ "$DOCS_ROOT" =~ ^/[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$ ]]
[[ "$DOCS_REVISION" =~ ^[0-9a-f]{40}$ ]]
target="$DOCS_ROOT/releases/$DOCS_REVISION"
mkdir -p "$target"
while IFS=' ' read -r checksum filename; do
    [[ "$checksum" =~ ^[0-9a-f]{64}$ && "$filename" =~ ^swagger-ui(-bundle.js|.css)$ ]]
    if [[ ! -f "$target/$filename" ]] || ! (cd "$target" && printf '%s  %s\n' "$checksum" "$filename" | sha256sum --check --status); then
        curl --fail --silent --show-error --location --connect-timeout 10 --max-time 90 \
            "https://raw.githubusercontent.com/swagger-api/swagger-ui/e89a91f13cdee432d97ca9d3d1ac1969ff797036/dist/$filename" \
            --output "$target/$filename.download"
        printf '%s  %s\n' "$checksum" "$target/$filename.download" | sha256sum --check --status
        mv "$target/$filename.download" "$target/$filename"
    fi
done < "$source_root/docs/infrastructure/swagger-ui/assets.sha256"
cp "$source_root/docs/infrastructure/swagger-ui/index.html" "$target/index.html"
cp "$source_root/docs/infrastructure/swagger-ui/swagger-initializer.js" "$target/swagger-initializer.js"
cp "$source_root/docs/openapi/api-chinh.json" "$target/api-chinh.json"
chmod 755 "$DOCS_ROOT" "$DOCS_ROOT/releases" "$target"
chmod 644 "$target"/*
ln -sfn "$target" "$DOCS_ROOT/current.next"
mv -Tf "$DOCS_ROOT/current.next" "$DOCS_ROOT/current"
printf 'Swagger UI 5.33.2 installed: %s\n' "$DOCS_REVISION"
