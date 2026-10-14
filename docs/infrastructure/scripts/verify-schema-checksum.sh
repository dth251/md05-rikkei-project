#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
schema="commerce_checksum_probe_$$"
[[ "$schema" =~ ^commerce_checksum_probe_[0-9]+$ ]]
probe="$project_root/.verification/$schema"
mkdir -p "$probe/docs/infrastructure/scripts" "$probe/docs/infrastructure/postgres/migrations/notification-service"
cp "$project_root/docs/infrastructure/scripts/apply-schema.sh" "$probe/docs/infrastructure/scripts/"
export project_root schema
# Execute the real runner and PostgreSQL, confined to an isolated test schema.
docker() {
    [[ "$1 $2 $3 $4 $5" == 'compose exec -T postgres psql' ]] || return 1
    shift 5
    command docker compose --project-directory "$project_root" --env-file "$project_root/.env" \
        -f "$project_root/docker-compose.yml" exec -T -e "PGOPTIONS=-c search_path=$schema" postgres psql "$@"
}
export -f docker
command docker compose --project-directory "$project_root" --env-file "$project_root/.env" \
    -f "$project_root/docker-compose.yml" exec -T postgres psql -U notification_user -d notification_db \
    -v ON_ERROR_STOP=1 -q -c "CREATE SCHEMA $schema"
cleanup() {
    command docker compose --project-directory "$project_root" --env-file "$project_root/.env" \
        -f "$project_root/docker-compose.yml" exec -T postgres psql -U notification_user -d notification_db \
        -v ON_ERROR_STOP=1 -q -c "DROP SCHEMA $schema CASCADE" >/dev/null
}
trap cleanup EXIT
file="$probe/docs/infrastructure/postgres/migrations/notification-service/001-checksum-probe.sql"
printf 'CREATE TABLE checksum_probe(id integer PRIMARY KEY);\n' > "$file"
bash "$probe/docs/infrastructure/scripts/apply-schema.sh" notification-service
# Replay the exact checksum, then prove a changed migration exits nonzero before DDL.
bash "$probe/docs/infrastructure/scripts/apply-schema.sh" notification-service
printf 'ALTER TABLE checksum_probe ADD COLUMN must_not_exist integer;\n' >> "$file"
if bash "$probe/docs/infrastructure/scripts/apply-schema.sh" notification-service > "$probe/mismatch.log" 2>&1; then
    echo 'FAIL: changed migration was accepted' >&2
    exit 1
fi
grep -q 'Schema checksum mismatch' "$probe/mismatch.log"
columns="$(docker compose exec -T postgres psql -U notification_user -d notification_db -Atc \
    "SELECT count(*) FROM information_schema.columns WHERE table_schema='$schema' AND column_name='must_not_exist'")"
[[ "$columns" == 0 ]]
echo 'PASS: migration applies/replays; checksum mismatch fails with no subsequent DDL'
