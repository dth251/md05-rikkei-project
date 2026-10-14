#!/usr/bin/env bash
set -euo pipefail
disk_kib="$(df -Pk /opt/omnichannel-ecommerce 2>/dev/null | awk 'NR==2 {print $4}')"
[[ -n "$disk_kib" ]] || disk_kib="$(df -Pk . | awk 'NR==2 {print $4}')"
available_kib="$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)"
[[ "$disk_kib" =~ ^[0-9]+$ && "$available_kib" =~ ^[0-9]+$ ]]
printf 'Available disk KiB=%s RAM KiB=%s\n' "$disk_kib" "$available_kib"
(( disk_kib >= 4194304 )) || { echo 'At least 4 GiB free disk required for observability retention and rollout headroom' >&2; exit 1; }
(( available_kib >= 1048576 )) || { echo 'At least 1 GiB available RAM required; enable profiles incrementally' >&2; exit 1; }
if command -v timedatectl >/dev/null && [[ -d /run/systemd/system ]]; then
    if [[ "$(timedatectl show -p NTPSynchronized --value)" != yes ]]; then
        # Some VPS networks block NTP. Check UTC independently rather than trusting
        # the local clock or claiming NTP has synchronized.
        for source in 'https://www.google.com/generate_204' 'https://www.cloudflare.com/cdn-cgi/trace'; do
            headers="$(curl -fsS --max-time 5 -H 'Cache-Control: no-cache' -D - -o /dev/null "$source?clock=$(date +%s)-$RANDOM")"
            remote_date="$(printf '%s\n' "$headers" | awk 'tolower($1)=="date:" {sub(/^[^:]*: */, ""); sub(/\r$/, ""); print; exit}')"
            test -n "$remote_date" || { echo 'Cannot independently verify host UTC' >&2; exit 1; }
            remote_epoch="$(date -u -d "$remote_date" +%s)"
            skew=$(( $(date -u +%s) - remote_epoch ))
            (( skew >= -5 && skew <= 5 )) || { echo 'Host UTC differs from HTTPS Date by more than 5 seconds' >&2; exit 1; }
        done
        echo 'PASS: host UTC verified against two HTTPS sources; NTP remains unsynchronized'
    fi
fi
echo 'PASS: resource and clock preflight; this is not a capacity guarantee'
