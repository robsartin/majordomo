#!/usr/bin/env bash
#
# Restores one archive into the database and attachment directory named by the
# environment. Takes the archive path as its only argument.
#
# Environment:
#   PGHOST PGUSER PGPASSWORD PGDATABASE   the server to restore INTO
#   BACKUP_AGE_IDENTITY                   age private key file
#   ATTACHMENTS_DIR                       where attachments are written
set -Eeuo pipefail
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"

archive="${1:-}"
[ -n "$archive" ] || die "usage: $(basename "$0") <archive.tar.age>"
[ -f "$archive" ] || die "no such archive: $archive"

require PGHOST PGUSER PGPASSWORD PGDATABASE BACKUP_AGE_IDENTITY ATTACHMENTS_DIR

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

log "decrypting $(basename "$archive")"
age -d -i "$BACKUP_AGE_IDENTITY" "$archive" | tar -x -C "$work"

[ -f "${work}/database.dump" ] || die "archive has no database.dump"
[ -f "${work}/attachments.tar" ] || die "archive has no attachments.tar"

log "restoring into ${PGDATABASE} on ${PGHOST}"
# --exit-on-error because the default is to report a problem and carry on,
# which yields a partial database and a zero exit status.
pg_restore --dbname="$PGDATABASE" --no-owner --no-privileges --exit-on-error \
    "${work}/database.dump"

log "restoring attachments into ${ATTACHMENTS_DIR}"
mkdir -p "$ATTACHMENTS_DIR"
tar -xf "${work}/attachments.tar" -C "$ATTACHMENTS_DIR"

log "restored from $(basename "$archive")"
sed 's/^/  /' "${work}/manifest.txt"
