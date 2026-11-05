#!/system/bin/sh

MODDIR=${0%/*}
LOG_FILE=/data/local/tmp/root_cert_manager.log
export PATH=/system/bin:/system/xbin

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [service] $1" >> "$LOG_FILE"
}

API=$(getprop ro.build.version.sdk)
if [ "$API" -lt 34 ]; then
    exit 0
fi

log "Late-service APEX verification"
sleep 2
/system/bin/sh "$MODDIR/apex-inject.sh" service
exit $?
