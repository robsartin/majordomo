#!/usr/bin/env bash
#
# The backup sidecar's main loop: back up daily, verify a restore weekly.
#
# Sleeping until the next run rather than polling keeps the container idle and
# makes the schedule one readable number. There is no cron daemon here on
# purpose — cron's environment is not this container's environment, and every
# missing variable would surface as a backup that silently did not happen.
#
# Environment: everything backup.sh needs, plus
#   BACKUP_AT              (optional, 03:30) UTC time of the daily run
#   BACKUP_VERIFY_EVERY    (optional, 7)     verify every Nth backup; 0 disables
set -Eeuo pipefail
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"

: "${BACKUP_AT:=03:30}"
: "${BACKUP_VERIFY_EVERY:=7}"

log "backup sidecar started; daily at ${BACKUP_AT} UTC, verify every ${BACKUP_VERIFY_EVERY} run(s)"

runs=0
while true; do
    wait_for=$(seconds_until "$BACKUP_AT" "$(date -u +%s)")
    log "sleeping ${wait_for}s until ${BACKUP_AT} UTC"
    sleep "$wait_for"

    # A failure must not kill the loop: tomorrow's backup is the best chance of
    # recovering from whatever broke today's, and an exited container is a
    # backup gap nobody is told about.
    if /usr/local/bin/backup.sh; then
        runs=$((runs + 1))
    else
        log "BACKUP FAILED (exit $?) — continuing; next attempt tomorrow"
        continue
    fi

    if [ "$BACKUP_VERIFY_EVERY" -gt 0 ] && [ $((runs % BACKUP_VERIFY_EVERY)) -eq 0 ]; then
        /usr/local/bin/verify-restore.sh || log "RESTORE VERIFICATION FAILED — continuing"
    fi
done
