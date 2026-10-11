#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" == 0 ]] || { echo 'Nginx switch requires root' >&2; exit 1; }
release="${1:?Provide the deployed release directory}"
domain="${DOMAIN:-md05-rikkei-project.duckdns.org}"
[[ "$domain" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*\.[a-zA-Z]{2,}$ ]]
curl --fail --silent --show-error --max-time 15 http://127.0.0.1:8080/actuator/health \
    | jq -e '.status == "UP"' > /dev/null
bash "$release/infrastructure/scripts/smoke-auth.sh" http://127.0.0.1:8080
destination=/etc/nginx/sites-available/omnichannel-auth.conf
[[ -f "$destination" && ! -L "$destination" ]] || { echo 'Expected project Nginx site missing' >&2; exit 1; }
certificate="/etc/letsencrypt/live/$domain"
[[ -f "$certificate/fullchain.pem" && -f "$certificate/privkey.pem" ]]
backup="$(mktemp /etc/nginx/sites-available/omnichannel-backup.XXXXXX)"
cp "$destination" "$backup"
restore_on_failure() {
    result=$?
    if [[ "$result" != 0 ]]; then
        cp "$backup" "$destination"
        nginx -t && systemctl reload nginx
        echo 'Restored the previous project Nginx site' >&2
    fi
    rm -f "$backup"
    exit "$result"
}
trap restore_on_failure EXIT
sed "s/__DOMAIN__/$domain/g" "$release/infrastructure/nginx/gateway.conf.template" \
    | sed 's/listen 80;/listen 443 ssl;/' > "$destination"
cat >> "$destination" <<EOF

server {
    listen 80;
    server_name $domain;
    return 301 https://\$host\$request_uri;
}
EOF
sed -i "/listen 443 ssl;/a\    ssl_certificate $certificate/fullchain.pem;\n    ssl_certificate_key $certificate/privkey.pem;\n    include /etc/letsencrypt/options-ssl-nginx.conf;\n    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;" "$destination"
nginx -t
systemctl reload nginx
SMOKE_HEALTH_PATH=/gateway-status bash "$release/infrastructure/scripts/smoke-auth.sh" "https://$domain"
printf 'HTTPS traffic now routes through API Gateway: %s\n' "$domain"
