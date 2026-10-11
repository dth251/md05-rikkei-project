#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" == 0 ]] || { echo 'Run network setup as root.' >&2; exit 1; }
command -v ufw >/dev/null
# Refuse to change the firewall around an unrelated public TCP listener.
while read -r address; do
    case "$address" in 127.*|\[::1\]:*|::1:*) continue ;; esac
    port="${address##*:}"
    case "$port" in 22|80|443) ;; *) echo "Unexpected public TCP listener on port $port; preserve its policy before proceeding." >&2; exit 1 ;; esac
done < <(ss -H -ltn | awk '{print $4}')
ufw allow 22/tcp comment 'SSH management'
ufw allow 80/tcp comment 'HTTP certificate renewal'
ufw allow 443/tcp comment 'Omnichannel HTTPS'
ufw default deny incoming
ufw default allow outgoing
ufw --force enable
ufw status verbose
echo 'Firewall enabled; existing rules retained. Docker services must remain loopback/private.'
