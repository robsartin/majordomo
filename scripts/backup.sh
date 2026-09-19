#!/usr/bin/env bash
#
# Takes one encrypted archive of everything that has to survive the house: the
# database and the attachments. Encryption happens here, before the file moves,
# so every destination holds ciphertext and no destination has to be trusted.
#
# Environment:
#   PGHOST PGUSER PGPASSWORD PGDATABASE   the server to dump
#   BACKUP_AGE_RECIPIENT                  age public key to encrypt to
#   BACKUP_DIR                            where archives are written
#   ATTACHMENTS_DIR                       majordomo.storage.base-dir, read-only
#   BACKUP_KEEP       (optional, 14)      archives to retain locally
#   BACKUP_REMOTES    (optional, none)    space-separated rclone destinations
set -Eeuo pipefail
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"

require PGHOST PGUSER PGPASSWORD PGDATABASE BACKUP_AGE_RECIPIENT BACKUP_DIR ATTACHMENTS_DIR
: "${BACKUP_KEEP:=14}"
: "${BACKUP_REMOTES:=}"

[ -d "$ATTACHMENTS_DIR" ] || die "ATTACHMENTS_DIR does not exist: $ATTACHMENTS_DIR"

stamp=$(date -u +%Y%m%dT%H%M%SZ)
archive="${BACKUP_DIR}/majordomo-${stamp}.tar.age"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

mkdir -p "$BACKUP_DIR"

log "dumping ${PGDATABASE} from ${PGHOST}"
pg_dump --format=custom --file="${work}/database.dump"

log "archiving attachments from ${ATTACHMENTS_DIR}"
tar -cf "${work}/attachments.tar" -C "$ATTACHMENTS_DIR" .

# Recorded inside the archive so a restore can be checked against what was
# taken, without a catalogue somewhere else that can go missing or go stale.
{
    echo "created-at=$(date -u +%FT%TZ)"
    echo "database=${PGDATABASE}"
    echo "source-host=${PGHOST}"
    echo "pg-dump-version=$(pg_dump --version | awk '{print $3}')"
    echo "attachment-files=$(find "$ATTACHMENTS_DIR" -type f | wc -l | tr -d ' ')"
} > "${work}/manifest.txt"

log "encrypting to ${archive}"
tar -cf - -C "$work" database.dump attachments.tar manifest.txt \
    | age -r "$BACKUP_AGE_RECIPIENT" -o "$archive"

for remote in $BACKUP_REMOTES; do
    log "copying to ${remote}"
    rclone copy "$archive" "$remote"
done

# Prune last: an archive is only allowed to age out once its replacement exists
# and has reached every destination.
surplus=$(find "$BACKUP_DIR" -maxdepth 1 -name 'majordomo-*.tar.age' \
    | sort | head -n "-${BACKUP_KEEP}")
if [ -n "$surplus" ]; then
    echo "$surplus" | while read -r old; do
        log "pruning $(basename "$old")"
        rm -f "$old"
    done
fi

log "done: $(basename "$archive") ($(du -h "$archive" | cut -f1))"
