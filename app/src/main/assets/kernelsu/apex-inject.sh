#!/system/bin/sh

MODDIR=${0%/*}
CERT_DIR="$MODDIR/system/etc/security/cacerts"
APEX_DIR=/apex/com.android.conscrypt/cacerts
FALLBACK_DIR=/system/etc/security/cacerts
STAGE_DIR=/data/local/tmp/root_cert_manager_cacerts
READY_FILE=/data/local/tmp/root_cert_manager.stage_ready
INJECTED_LIST=/data/local/tmp/root_cert_manager.injected_names
LOG_FILE=/data/local/tmp/root_cert_manager.log
MODE=${1:-service}
export PATH=/system/bin:/system/xbin

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [apex/$MODE] $1" >> "$LOG_FILE"
}

is_cert_name() {
    case "$1" in
        [0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F].[0-9]*)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

count_certs() {
    directory="$1"
    count=0
    for file in "$directory"/*; do
        [ -f "$file" ] || continue
        is_cert_name "${file##*/}" || continue
        count=$((count + 1))
    done
    echo "$count"
}

API=$(getprop ro.build.version.sdk)
if [ "$API" -lt 34 ]; then
    exit 0
fi

if [ ! -d "$APEX_DIR" ]; then
    log "Conscrypt APEX cacerts directory not found; relying on the system overlay"
    exit 0
fi

MODULE_CERT_COUNT=$(count_certs "$CERT_DIR")
if [ "${MODULE_CERT_COUNT:-0}" -eq 0 ]; then
    log "No managed certificates; APEX untouched"
    exit 0
fi

BOOT_ID=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)
[ -n "$BOOT_ID" ] || BOOT_ID=unknown-boot

stage_is_current() {
    [ -d "$STAGE_DIR" ] || return 1
    [ -f "$READY_FILE" ] || return 1
    [ -f "$INJECTED_LIST" ] || return 1
    [ "$(cat "$READY_FILE" 2>/dev/null)" = "$BOOT_ID" ] || return 1
    [ "$(count_certs "$STAGE_DIR")" -ge "$MODULE_CERT_COUNT" ]
}

target_mount_is_current() {
    local_pid="$1"
    sample_name=$(head -n 1 "$INJECTED_LIST" 2>/dev/null)
    [ -n "$sample_name" ] || return 0
    if [ "$local_pid" = "self" ]; then
        [ -f "$APEX_DIR/$sample_name" ]
    else
        [ -d "/proc/$local_pid/ns/mnt" ] || return 1
        nsenter --mount="/proc/$local_pid/ns/mnt" -- /system/bin/sh \
            -c "test -f '$APEX_DIR/$sample_name'" 2>/dev/null
    fi
}

mount_for_self() {
    if target_mount_is_current self; then
        log "Current namespace already current"
        return 0
    fi
    if mount --bind "$STAGE_DIR" "$APEX_DIR" 2>/dev/null; then
        log "Bound staged store in current namespace"
    else
        log "WARN: bind mount failed in current namespace"
    fi
}

mount_for_pid() {
    pid="$1"
    [ -n "$pid" ] || return 0
    [ -d "/proc/$pid/ns/mnt" ] || return 0
    if target_mount_is_current "$pid"; then
        log "PID $pid already sees the staged store"
        return 0
    fi
    if nsenter --mount="/proc/$pid/ns/mnt" -- /system/bin/mount \
        --bind "$STAGE_DIR" "$APEX_DIR" 2>/dev/null; then
        log "Injected into PID $pid"
    else
        log "WARN: nsenter mount failed for PID $pid"
    fi
}

if ! stage_is_current; then
    log "Building a new staged certificate store"
    if mountpoint -q "$STAGE_DIR" 2>/dev/null; then
        umount "$STAGE_DIR" 2>/dev/null
    fi
    rm -rf "$STAGE_DIR" 2>/dev/null
    mkdir -p "$STAGE_DIR" || {
        log "ERROR: cannot create staging directory"
        exit 1
    }
    if mount -t tmpfs tmpfs "$STAGE_DIR" 2>/dev/null; then
        log "Mounted tmpfs staging directory"
    else
        log "WARN: tmpfs unavailable; using ordinary directory"
    fi

    SOURCE="$APEX_DIR"
    SOURCE_COUNT=$(count_certs "$SOURCE")
    if [ "${SOURCE_COUNT:-0}" -eq 0 ]; then
        SOURCE="$FALLBACK_DIR"
        SOURCE_COUNT=$(count_certs "$SOURCE")
    fi
    if [ "${SOURCE_COUNT:-0}" -eq 0 ]; then
        log "ERROR: no system certificates available to seed the staged store"
        exit 1
    fi
    log "Seeding from $SOURCE ($SOURCE_COUNT certificates)"
    source_copy_failed=0
    for source_cert in "$SOURCE"/*; do
        [ -f "$source_cert" ] || continue
        is_cert_name "${source_cert##*/}" || continue
        if ! cp -a "$source_cert" "$STAGE_DIR"/ 2>/dev/null; then
            log "ERROR: failed to copy ${source_cert##*/}"
            source_copy_failed=1
        fi
    done
    if [ "$source_copy_failed" -ne 0 ]; then
        log "ERROR: system certificate copy was incomplete"
        exit 1
    fi
    : > "$INJECTED_LIST"

    for cert in "$CERT_DIR"/*; do
        [ -f "$cert" ] || continue
        name=${cert##*/}
        is_cert_name "$name" || continue

        base=${name%%.*}
        index=0
        destination="$STAGE_DIR/$base.$index"
        while [ -e "$destination" ] && ! cmp -s "$cert" "$destination"; do
            index=$((index + 1))
            destination="$STAGE_DIR/$base.$index"
            if [ "$index" -gt 999 ]; then
                log "ERROR: too many hash collisions for $base"
                exit 1
            fi
        done
        if [ ! -e "$destination" ]; then
            if cp -f "$cert" "$destination"; then
                echo "${destination##*/}" >> "$INJECTED_LIST"
            else
                log "ERROR: failed to stage $name"
                exit 1
            fi
        fi
    done

    chown 0:0 "$STAGE_DIR" "$STAGE_DIR"/* 2>/dev/null
    chmod 755 "$STAGE_DIR" 2>/dev/null
    chmod 644 "$STAGE_DIR"/* 2>/dev/null

    APEX_CONTEXT=$(ls -Zd "$APEX_DIR" 2>/dev/null | awk '{print $1}')
    case "$APEX_CONTEXT" in
        u:*) ;;
        *) APEX_CONTEXT="u:object_r:system_file:s0" ;;
    esac
    if ! chcon -R "$APEX_CONTEXT" "$STAGE_DIR" 2>/dev/null; then
        log "WARN: could not apply APEX SELinux context; trying fallback labels"
        chcon -R u:object_r:system_security_cacerts_file:s0 "$STAGE_DIR" 2>/dev/null \
            || chcon -R u:object_r:system_file:s0 "$STAGE_DIR" 2>/dev/null || true
    fi

    TOTAL_COUNT=$(count_certs "$STAGE_DIR")
    if [ "${TOTAL_COUNT:-0}" -lt "${SOURCE_COUNT:-0}" ]; then
        log "ERROR: staged store is incomplete"
        exit 1
    fi
    for injected_name in $(cat "$INJECTED_LIST" 2>/dev/null); do
        if [ ! -f "$STAGE_DIR/$injected_name" ]; then
            log "ERROR: staged certificate missing: $injected_name"
            exit 1
        fi
    done
    echo "$BOOT_ID" > "$READY_FILE"
    INJECTED_COUNT=$(wc -l < "$INJECTED_LIST" 2>/dev/null | tr -d ' ')
    log "Staged $TOTAL_COUNT certificates ($INJECTED_COUNT managed additions)"
else
    log "Reusing staged store for this boot"
fi

if [ ! -s "$INJECTED_LIST" ]; then
    log "All managed certificates are already present in the system store"
    exit 0
fi

mount_for_self
mount_for_pid 1
for pid in $(pidof zygote 2>/dev/null) $(pidof zygote64 2>/dev/null); do
    mount_for_pid "$pid"
done

if target_mount_is_current 1; then
    log "Verified staged store in init namespace"
else
    log "WARN: init namespace verification failed"
fi

log "APEX injection finished"
exit 0
