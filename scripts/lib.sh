#!/usr/bin/env bash
# Shared helpers for the backup scripts. Sourced, not executed.

# Fails loudly listing every missing variable at once, rather than dying on the
# first one and making the operator rerun to discover the next. A backup that
# quietly skips a step it could not configure is worse than one that refuses to
# start.
require() {
    local missing=()
    local name
    for name in "$@"; do
        if [ -z "${!name:-}" ]; then
            missing+=("$name")
        fi
    done
    if [ "${#missing[@]}" -gt 0 ]; then
        echo "ERROR: missing required environment: ${missing[*]}" >&2
        exit 64
    fi
}

log() {
    echo "[$(date -u +%FT%TZ)] $*"
}

die() {
    echo "ERROR: $*" >&2
    exit 1
}

# Exact row counts for every user table, as `schema.table=count` lines.
#
# Shared by backup.sh and verify-restore.sh so the two sides of the comparison
# are the same question asked twice, not two queries that can drift apart.
# pg_stat_user_tables.n_live_tup would be cheaper and is an estimate, which is
# no use for deciding whether rows went missing.
table_rows() {
    psql --no-align --tuples-only --quiet "$@" --command "
        SELECT table_schema || '.' || table_name || '=' ||
               (xpath('/row/cnt/text()',
                      query_to_xml(format('SELECT count(*) AS cnt FROM %I.%I',
                                          table_schema, table_name),
                                   false, true, '')))[1]::text
        FROM information_schema.tables
        WHERE table_type = 'BASE TABLE'
          AND table_schema NOT IN ('pg_catalog', 'information_schema')
        ORDER BY 1"
}
