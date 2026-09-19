#!/usr/bin/env bash
#
# Restores an archive into a scratch database and checks that what came back
# matches what was taken. Exits non-zero if it does not.
#
# This exists because every cheaper check passes on a broken backup. A dump that
# lost rows is still a well-formed file, still decrypts, still restores, and
# still exits zero. Only replaying it and counting catches that, which is why
# this runs on a schedule rather than being a thing someone means to try.
#
# Takes the archive path, or defaults to the newest in BACKUP_DIR.
#
# Environment:
#   PGHOST PGUSER PGPASSWORD    a server to create the scratch database on
#   BACKUP_AGE_IDENTITY         age private key file
#   BACKUP_DIR                  searched when no archive is given
set -Eeuo pipefail
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"

require PGHOST PGUSER PGPASSWORD BACKUP_AGE_IDENTITY

archive="${1:-}"
if [ -z "$archive" ]; then
    require BACKUP_DIR
    archive=$(find "$BACKUP_DIR" -maxdepth 1 -name 'majordomo-*.tar.age' | sort | tail -1)
    [ -n "$archive" ] || die "no archives found in ${BACKUP_DIR}"
fi
[ -f "$archive" ] || die "no such archive: $archive"

scratch="majordomo_verify_$$"
work=$(mktemp -d)
cleanup() {
    rm -rf "$work"
    psql --dbname=postgres --quiet --command "DROP DATABASE IF EXISTS ${scratch}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

log "verifying $(basename "$archive")"
age -d -i "$BACKUP_AGE_IDENTITY" "$archive" | tar -x -C "$work"
[ -f "${work}/manifest.txt" ] || die "archive has no manifest"
[ -f "${work}/database.dump" ] || die "archive has no database.dump"

psql --dbname=postgres --quiet --command "CREATE DATABASE ${scratch}" >/dev/null
pg_restore --dbname="$scratch" --no-owner --no-privileges --exit-on-error \
    "${work}/database.dump" >/dev/null

failures=0

# Compare row counts table by table, reporting every mismatch rather than the
# first: "which tables came back short" is the useful answer.
while IFS='=' read -r table expected; do
    case "$table" in
        *.*) ;;
        *) continue ;;
    esac
    actual=$(table_rows --dbname="$scratch" | awk -F= -v t="$table" '$1 == t {print $2}')
    if [ -z "$actual" ]; then
        echo "  ${table}: MISSING (expected ${expected} rows)"
        failures=$((failures + 1))
    elif [ "$actual" != "$expected" ]; then
        echo "  ${table}: ${actual} rows, expected ${expected}"
        failures=$((failures + 1))
    else
        echo "  ${table}: ${actual} rows OK"
    fi
done < "${work}/manifest.txt"

expected_files=$(awk -F= '/^attachment-files=/ {print $2}' "${work}/manifest.txt")
mkdir -p "${work}/attachments"
tar -xf "${work}/attachments.tar" -C "${work}/attachments"
actual_files=$(find "${work}/attachments" -type f | wc -l | tr -d ' ')
if [ "$actual_files" != "$expected_files" ]; then
    echo "  attachments: ${actual_files} files, expected ${expected_files}"
    failures=$((failures + 1))
else
    echo "  attachments: ${actual_files} files OK"
fi

if [ "$failures" -gt 0 ]; then
    die "restore verification FAILED for $(basename "$archive"): ${failures} mismatch(es)"
fi

log "restore verified OK: $(basename "$archive")"
