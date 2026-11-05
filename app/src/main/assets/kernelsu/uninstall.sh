#!/system/bin/sh

MODDIR=${0%/*}
APEX_DIR=/apex/com.android.conscrypt/cacerts
STAGE_DIR=/data/local/tmp/root_cert_manager_cacerts
READY_FILE=/data/local/tmp/root_cert_manager.stage_ready
INJECTED_LIST=/data/local/tmp/root_cert_manager.injected_names
LOG_FILE=/data/local/tmp/root_cert_manager.log
export PATH=/system/bin:/system/xbin

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [uninstall] $1" >> "$LOG_FILE"
}

sample_name=$(head -n 1 "$INJECTED_LIST" 2>/dev/null)

has_stage() {
    pid="$1"
    [ -n "$sample_name" ] || return 1
    if [ "$pid" = "self" ]; then
        [ -f "$APEX_DIR/$sample_name" ]
    else
        [ -d "/proc/$pid/ns/mnt" ] || return 1
        nsenter --mount="/proc/$pid/ns/mnt" -- /system/bin/sh \
            -c "test -f '$APEX_DIR/$sample_name'" 2>/dev/null
    fi
}

unmount_from_pid() {
    pid="$1"
    has_stage "$pid" || return 0
    attempt=0
    while [ "$attempt" -lt 3 ] && has_stage "$pid"; do
        if [ "$pid" = "self" ]; then
            umount "$APEX_DIR" 2>/dev/null
        else
            nsenter --mount="/proc/$pid/ns/mnt" -- /system/bin/umount "$APEX_DIR" 2>/dev/null
        fi
        attempt=$((attempt + 1))
    done
    log "Cleared staged store for namespace $pid"
}

unmount_from_pid self
unmount_from_pid 1
for pid in $(pidof zygote 2>/dev/null) $(pidof zygote64 2>/dev/null); do
    unmount_from_pid "$pid"
done

if mountpoint -q "$STAGE_DIR" 2>/dev/null; then
    umount "$STAGE_DIR" 2>/dev/null
fi
rm -rf "$STAGE_DIR" "$READY_FILE" "$INJECTED_LIST" 2>/dev/null
log "Uninstall cleanup complete; reboot recommended"
