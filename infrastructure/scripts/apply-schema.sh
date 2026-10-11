#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
service="${1:?Set service name}"
case "$service" in
    auth-service|product-service|order-service|payment-service|notification-service) ;;
    api-gateway) exit 0 ;;
    *) echo 'Unsupported schema service' >&2; exit 1 ;;
esac
name="${service%-service}"
directory="infrastructure/postgres/migrations/$service"
[[ -d "$directory" ]] || exit 0
shopt -s nullglob
for migration in "$directory"/[0-9][0-9][0-9]-*.sql; do
    filename="${migration##*/}"
    [[ "$filename" =~ ^([0-9]{3})-[a-z0-9-]+\.sql$ ]] || exit 1
    version="${BASH_REMATCH[1]}"
    checksum="$(sha256sum "$migration" | cut -d ' ' -f 1)"
    [[ "$checksum" =~ ^[0-9a-f]{64}$ ]]
    {
        printf "BEGIN;\nSET LOCAL lock_timeout='15s';\nSET LOCAL statement_timeout='60s';\nSELECT pg_advisory_xact_lock(730590);\n"
        printf 'CREATE TABLE IF NOT EXISTS commerce_schema_versions (version varchar(3) PRIMARY KEY, checksum varchar(64) NOT NULL, applied_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP);\n'
        printf "SELECT EXISTS (SELECT 1 FROM commerce_schema_versions WHERE version='%s' AND checksum<>'%s') AS mismatch \\gset\n" "$version" "$checksum"
        printf '\\if :mismatch\n\\echo Schema checksum mismatch\n\\quit 1\n\\endif\n'
        printf "SELECT NOT EXISTS (SELECT 1 FROM commerce_schema_versions WHERE version='%s') AS apply_migration \\gset\n" "$version"
        printf '\\if :apply_migration\n'
        cat "$migration"
        printf "\nINSERT INTO commerce_schema_versions(version,checksum) VALUES('%s','%s');\n" "$version" "$checksum"
        printf '\\endif\nCOMMIT;\n'
    } | docker compose exec -T postgres psql -U "${name}_user" -d "${name}_db" \
            -v ON_ERROR_STOP=1 -q -o /dev/null
    printf 'Schema verified: %s / %s\n' "$service" "$filename"
done
