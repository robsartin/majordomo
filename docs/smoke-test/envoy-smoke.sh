#!/usr/bin/env bash
# End-to-end smoke test for the envoy job-scoring service.
#
# Walks the full POST /api/envoy/postings → POST .../{id}/score → GET
# /api/envoy/reports flow against a locally-running app, using form login
# to obtain a Spring Security session cookie. Exits non-zero on any HTTP
# error or unexpected response shape.
#
# Prereqs:
#   - docker compose up -d db redis      # Postgres on :3946, Redis on :6379
#   - export ANTHROPIC_API_KEY=sk-ant-... # required: scoring calls Anthropic
#   - ./mvnw spring-boot:run              # in another terminal
#
# Usage:
#   ./docs/smoke-test/envoy-smoke.sh
#
# Optional env overrides:
#   BASE_URL      (default http://localhost:8080)
#   USERNAME      (default robsartin)
#   PASSWORD      (default xyzzyPLAN9)
#   ORGANIZATION_ID  (default 019606a0-0000-7000-8000-000000000003 — seed org)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USERNAME="${USERNAME:-robsartin}"
PASSWORD="${PASSWORD:-xyzzyPLAN9}"
ORGANIZATION_ID="${ORGANIZATION_ID:-019606a0-0000-7000-8000-000000000003}"

COOKIES="$(mktemp -t envoy-smoke-cookies.XXXXXX)"
trap 'rm -f "$COOKIES"' EXIT

step() { printf "\n\033[1;36m▸ %s\033[0m\n" "$*"; }
ok()   { printf "  \033[1;32m✓\033[0m %s\n" "$*"; }
fail() { printf "  \033[1;31m✗ %s\033[0m\n" "$*" >&2; exit 1; }

require() {
    command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}
require curl
require jq

step "1/5 health check"
status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/actuator/health")
[[ "$status" == "200" ]] || fail "actuator/health returned $status (is the app running on $BASE_URL?)"
ok "app is up at $BASE_URL"

step "2/5 form login as $USERNAME"
login_status=$(curl -s -o /dev/null -w '%{http_code}' \
    -c "$COOKIES" -b "$COOKIES" \
    -X POST "$BASE_URL/login" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "username=$USERNAME" \
    --data-urlencode "password=$PASSWORD")
# Spring Security form login returns 302 on success
[[ "$login_status" =~ ^30[0-9]$ ]] \
    || fail "login returned $login_status, expected 3xx (wrong creds? user not seeded?)"
ok "session cookie acquired"

step "3/5 ingest a job posting"
ingest_response=$(curl -fsS -c "$COOKIES" -b "$COOKIES" \
    -X POST "$BASE_URL/api/envoy/postings?organizationId=$ORGANIZATION_ID" \
    -H 'Content-Type: application/json' \
    -d @- <<'EOF'
{
  "type": "manual",
  "payload": "Senior Backend Engineer at Acme Corp. We're hiring a Staff/Senior backend engineer for our platform team. Stack: Java 21, Spring Boot 3, Postgres 17, AWS. Fully remote across the US. Compensation: $230k base, target $300k OTE, equity. Series C, $200M ARR, profitable. We don't expect 60-hour weeks; we work hard and we go home. Reports to a named EM with a clear quarterly roadmap.",
  "hints": {
    "company": "Acme Corp",
    "title": "Senior Backend Engineer",
    "location": "Remote (US)"
  }
}
EOF
)
posting_id=$(echo "$ingest_response" | jq -r '.id')
[[ -n "$posting_id" && "$posting_id" != "null" ]] || fail "ingest response had no .id: $ingest_response"
ok "posting ingested, id=$posting_id"

step "4/5 score the posting (calls Anthropic — costs a few cents)"
[[ -n "${ANTHROPIC_API_KEY:-}" ]] \
    || fail "ANTHROPIC_API_KEY not set in the app's environment — restart spring-boot:run with it exported"
score_response=$(curl -fsS -c "$COOKIES" -b "$COOKIES" \
    -X POST "$BASE_URL/api/envoy/postings/$posting_id/score?organizationId=$ORGANIZATION_ID")
final_score=$(echo "$score_response" | jq -r '.finalScore')
recommendation=$(echo "$score_response" | jq -r '.recommendation')
[[ -n "$final_score" && "$final_score" != "null" ]] \
    || fail "score response missing .finalScore: $score_response"
ok "scored: finalScore=$final_score, recommendation=$recommendation"

step "5/5 list reports for this org"
list_response=$(curl -fsS -c "$COOKIES" -b "$COOKIES" \
    "$BASE_URL/api/envoy/reports?organizationId=$ORGANIZATION_ID&limit=10")
count=$(echo "$list_response" | jq -r '.items | length')
[[ "$count" -ge 1 ]] || fail "report list was empty: $list_response"
ok "reports list returned $count item(s)"

printf "\n\033[1;32mSmoke test PASSED.\033[0m Posting %s scored at %s (%s).\n" \
    "$posting_id" "$final_score" "$recommendation"
