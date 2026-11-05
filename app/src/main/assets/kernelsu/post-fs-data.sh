#!/system/bin/sh

MODDIR=${0%/*}
LOG_FILE=/data/local/tmp/root_cert_manager.log
export PATH=/system/bin:/system/xbin

mkdir -p /data/local/tmp 2>/dev/null
: > "$LOG_FILE"
chmod 600 "$LOG_FILE" 2>/dev/null

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [post-fs-data] $1" >> "$LOG_FILE"
}

log "Root Certificate Manager module starting"
API=$(getprop ro.build.version.sdk)
log "Android API: $API"

if [ "$API" -lt 34 ]; then
    log "Android < 14: certificates are provided through the KernelSU system overlay"
    exit 0
fi

/system/bin/sh "$MODDIR/apex-inject.sh" boot
exit $?
