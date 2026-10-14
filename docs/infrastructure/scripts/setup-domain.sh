#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" == 0 ]] || { echo 'Domain setup requires root' >&2; exit 1; }
domain="${DOMAIN:-md05-rikkei-project.duckdns.org}"
expected_ip="${VPS_HOST:-42.96.2.60}"
: "${TLS_EMAIL:?Set certificate contact email}"
[[ "$domain" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*\.[a-zA-Z]{2,}$ ]]
[[ "$TLS_EMAIL" == *@*.* ]]
release="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
resolved="$(getent ahostsv4 "$domain" | awk 'NR == 1 { print $1 }')"
[[ "$resolved" == "$expected_ip" ]] || { echo 'Domain A record does not match VPS_HOST' >&2; exit 1; }
curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8081/actuator/health \
    | jq -e '.status == "UP"' > /dev/null
destination=/etc/nginx/sites-available/omnichannel-auth.conf
enabled=/etc/nginx/sites-enabled/omnichannel-auth.conf
if [[ ! -e "$destination" ]]; then
    sed "s/__DOMAIN__/$domain/g" "$release/docs/infrastructure/nginx/auth.conf.template" > "$destination"
    chmod 0644 "$destination"
fi
if [[ -e "$enabled" || -L "$enabled" ]]; then
    [[ "$(readlink -f "$enabled")" == "$destination" ]] \
        || { echo 'Existing enabled site has a different target' >&2; exit 1; }
else
    ln -s "$destination" "$enabled"
fi
nginx -t
systemctl enable --now nginx
systemctl reload nginx
if command -v ufw > /dev/null 2>&1 && ufw status | grep -q '^Status: active'; then
    ufw allow 80/tcp
    ufw allow 443/tcp
fi
certbot --nginx --non-interactive --agree-tos --email "$TLS_EMAIL" --redirect -d "$domain"
nginx -t
systemctl reload nginx
curl --fail --silent --show-error --max-time 20 "https://$domain/actuator/health" \
    | jq -e '.status == "UP"' > /dev/null
bash "$release/docs/infrastructure/scripts/smoke-auth.sh" "https://$domain"
systemctl enable --now certbot.timer
printf 'HTTPS Auth domain verified: https://%s\n' "$domain"
