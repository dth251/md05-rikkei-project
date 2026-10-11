#!/usr/bin/env bash
set -euo pipefail
duration="${1:-30}"
[[ "$duration" =~ ^[0-9]+$ && "$duration" -ge 1 && "$duration" -le 300 ]]
read_cpu() {
    awk '/^cpu / { idle=$5+$6; total=0; for(i=2;i<=NF;i++) total+=$i; print total, idle; exit }' /proc/stat
}
read -r previous_total previous_idle < <(read_cpu)
printf 'time_utc,cpu_percent,ram_available_mib,swap_used_mib,load_1m\n'
for (( elapsed=0; elapsed<duration; elapsed+=2 )); do
    sleep 2
    read -r total idle < <(read_cpu)
    cpu="$(awk -v t="$((total-previous_total))" -v i="$((idle-previous_idle))" 'BEGIN{printf "%.2f", (t>0 ? 100*(t-i)/t : 0)}')"
    memory="$(awk '/^MemAvailable:/{a=$2}/^SwapTotal:/{t=$2}/^SwapFree:/{f=$2}END{printf "%.2f,%.2f",a/1024,(t-f)/1024}' /proc/meminfo)"
    printf '%s,%s,%s,%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$cpu" "$memory" "$(cut -d' ' -f1 /proc/loadavg)"
    previous_total="$total"; previous_idle="$idle"
done
