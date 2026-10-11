#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
while IFS='=' read -r name value; do
    if [[ "$name" == AUTH_DB_PASSWORD ]]; then
        export AUTH_SMOKE_DB_PASSWORD="$value"
    fi
done < .env
export AUTH_SMOKE_DB_URL=jdbc:postgresql://localhost:15432/auth_db
export AUTH_SMOKE_URL=http://127.0.0.1:8081
export AUTH_SMOKE_RESTART=true
cd auth-service
./gradlew liveTest --no-daemon
