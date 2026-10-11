#!/usr/bin/env bash
set -euo pipefail
base="${1:-http://127.0.0.1:8081}"
umask 077
temporary="$(mktemp -d)"
trap 'rm -f "$temporary/response.json"; rmdir "$temporary"' EXIT
email="smoke-$(openssl rand -hex 12)@example.com"
password="$(openssl rand -hex 20)"
call() {
    local method="$1" path="$2" expected="$3" body="${4:-}" bearer="${5:-}"
    local status
    local arguments=(--silent --show-error --connect-timeout 10 --max-time 20 --output "$temporary/response.json" --write-out '%{http_code}' --request "$method" --header 'Content-Type: application/json')
    [[ -z "$bearer" ]] || arguments+=(--header "Authorization: Bearer $bearer")
    if [[ -n "$body" ]]; then
        status="$(printf '%s' "$body" | curl "${arguments[@]}" --data-binary @- "$base$path")"
    else
        status="$(curl "${arguments[@]}" "$base$path")"
    fi
    [[ "$status" == "$expected" ]] || { echo "$method $path expected $expected, received $status" >&2; exit 1; }
}
call GET /actuator/health 200
jq -e '.status == "UP"' "$temporary/response.json" > /dev/null
registration="$(jq -nc --arg email "$email" --arg password "$password" '{fullName:"VPS Smoke",email:$email,password:$password}')"
credentials="$(jq -nc --arg email "$email" --arg password "$password" '{email:$email,password:$password}')"
call POST /api/auth/register 201 "$registration"
id="$(jq -r '.data.id' "$temporary/response.json")"
jq -e '.success == true and .data.role == "USER" and (.data | has("passwordHash") | not)' "$temporary/response.json" > /dev/null
call POST /api/auth/login 200 "$credentials"
token="$(jq -r '.data.accessToken' "$temporary/response.json")"
call GET /api/auth/me 200 '' "$token"
jq -e --argjson id "$id" '.data.id == $id' "$temporary/response.json" > /dev/null
call GET /api/auth/me 401
call GET "/api/auth/admin/users/$id" 403 '' "$token"
call POST /api/auth/register 409 "$registration"
echo 'PASS: VPS Auth health/register/login/me, 401/403/409; no credentials printed.'
