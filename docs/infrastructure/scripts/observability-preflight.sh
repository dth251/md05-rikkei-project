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
    [[ "$(timedatectl show -p NTPSynchronized --value)" == yes ]] \
        || { echo 'Synchronize host clock before tracing/payment/load verification' >&2; exit 1; }
fi
echo 'PASS: resource and clock preflight; this is not a capacity guarantee'
