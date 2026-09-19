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
