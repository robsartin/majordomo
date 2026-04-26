# Envoy smoke test

End-to-end check that the envoy job-scoring service works against a real
Postgres + Redis + Anthropic stack. Useful as a manual QA after merges that
touch envoy or its dependencies (Spring Boot bumps, Anthropic SDK bumps,
Flyway migrations, security config changes).

The unit and `@WebMvcTest` slices in `src/test/` already cover the contract
of each piece. This script verifies they're correctly wired together when
the app boots against real infrastructure.

## Prereqs

```bash
docker compose up -d db redis             # Postgres on :3946, Redis on :6379
export ANTHROPIC_API_KEY=sk-ant-...       # required: scoring calls Anthropic
ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY ./mvnw spring-boot:run
```

The `ANTHROPIC_API_KEY` must be exported in the same shell that runs
`spring-boot:run` — Maven inherits the parent shell's environment.

## Run

In a third terminal:

```bash
./docs/smoke-test/envoy-smoke.sh
```

The script:

1. Verifies `/actuator/health` returns 200
2. Logs in via `POST /login` (form, `robsartin` / `xyzzyPLAN9`), saves the session cookie
3. `POST /api/envoy/postings` with a manual paste of a synthetic job posting
4. `POST /api/envoy/postings/{id}/score` — this calls Anthropic for real
5. `GET /api/envoy/reports` — verifies the persisted report shows up

Exits 0 with a green PASSED line on success, non-zero on any failure.

## Cost

One scoring call is roughly 4–6k input tokens (rubric + posting body) and a
few hundred output tokens. At Sonnet 4.6 prices that's well under a cent
per run.

## Overrides

Environment variables (defaults in parens):

- `BASE_URL` (`http://localhost:8080`)
- `USERNAME` (`robsartin`)
- `PASSWORD` (`xyzzyPLAN9`)
- `ORGANIZATION_ID` (`019606a0-0000-7000-8000-000000000003` — the seed org from `V2__seed_default_user.sql`)

## When this should be expanded

Items currently out of scope but worth adding when the surface grows:

- `PUT /api/envoy/rubrics/{name}` round-trip (author a custom rubric, score
  against it, verify the report references the new version)
- `UrlFetchSource` / `GreenhouseApiSource` paths — both need real network
  egress and a stable target URL/board, so they're better as separate
  opt-in scripts
- Audit log assertion — verify `JobPostingIngested` and `JobPostingScored`
  produced `audit_log_entries` rows
