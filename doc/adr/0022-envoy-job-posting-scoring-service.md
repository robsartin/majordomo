# 22. Envoy: rubric-based job-posting scoring service

Date: 2026-04-28

## Status

Accepted

## Context

Majordomo's household-staff metaphor (ADR-0002) covers physical assets, contacts,
schedules, and finances, but the same machinery is well suited to evaluating
written documents against a structured set of criteria. The original prompt was
job hunting: triaging a stream of inbound postings against a personal rubric is
tedious, inconsistent, and easily skipped.

We needed a service that:

- Ingests a job posting from any of several sources (manual paste, raw URL,
  Greenhouse API) and normalises it into a single domain model.
- Scores the posting against an explicit, **versioned** rubric — categories
  with tiered point values, disqualifiers, flag penalties, and recommendation
  thresholds — so the verdict is reproducible and auditable.
- Uses an LLM only where it earns its keep: free-text understanding (which
  tier fits, why a flag fires) — never for the arithmetic, the threshold
  logic, or the recommendation.
- Produces a durable, queryable history so the user can compare postings,
  re-score against a new rubric version, and look back at *why* a posting was
  scored the way it was months later.
- Notifies the user when a posting clears the highest threshold (`APPLY_NOW`)
  without spamming for everything else.

This is a natural fit for a new household-staff service. Following ADR-0002,
we name it **The Envoy** — the household member who carries messages out into
the world and brings back assessments. Envoy lives inside the existing
hexagonal monolith as a vertical slice under `com.majordomo.{layer}.envoy`,
exactly like Steward, Concierge, Herald, and Ledger (ADR-0004).

## Decision

We will introduce Envoy as a hexagonal service whose architecture is
constrained by the following decisions, each tied to the closed issue that
shipped it.

### Rubric-based scoring with LLM-as-grader (#119)

A `Rubric` is a versioned, JSONB-persisted document containing:

- `categories` — each with a `key`, `description`, `maxPoints`, and a list of
  `tiers` (label, points, criteria text).
- `disqualifiers` — hard-fail signals (e.g. `ON_SITE_ONLY`,
  `NON_ENGINEERING`); any hit forces the recommendation to `SKIP` regardless
  of points.
- `flags` — soft penalties (e.g. `UNPAID_TEST` -5 points).
- `thresholds` — the integer cut-points that map a final score to a
  `Recommendation` of `APPLY_NOW`, `APPLY`, `CONSIDER`, or `SKIP`.

The LLM (default model `claude-sonnet-4-6` via the official Anthropic Java
SDK) is asked only to choose a tier per category and fire flags/disqualifiers
where warranted, returning structured JSON. Java code in
`application.envoy.ScoreAssembler` then performs all arithmetic, applies
disqualifier short-circuits, and derives the recommendation from the rubric
thresholds. The assembler is unit-tested without the model.

### Versioned rubrics with system defaults and per-org overrides

`envoy_rubric` is keyed by `(organization_id, name, version)` with
`organization_id IS NULL` reserved for system-default templates that every
org sees until it authors its own version (`PUT /api/envoy/rubrics/{name}`).
A Flyway seed (`V15`) ships a `default` rubric so a brand-new org can score
without authoring anything. The system-default vs. per-org distinction is
enforced by two partial unique indexes rather than a discriminator column.

### Multi-rubric scoring (#149)

A single posting can be scored against several rubrics in one call
(`POST /api/envoy/postings/{id}/score-all?rubricNames=…`). Each rubric
produces its own persisted `ScoreReport` and its own `JobPostingScored`
domain event. The call fails fast: if any rubric cannot be resolved, no
reports are persisted. This lets users keep e.g. a "principal-engineer
rubric" alongside an "early-stage rubric" and view both verdicts side by
side without rewriting either.

### Persisted ScoreReport with usage and latency (#161, #167)

Every score produces an immutable `ScoreReport` row that captures the rubric
id and version, all category scores (with rationale strings), all flag hits,
the raw and final scores, the recommendation, the model identifier, and the
LLM call's `input_tokens`, `output_tokens`, and `latency_ms`. The usage
columns are nullable so legacy rows and providers that omit usage data
remain valid. Persisting this on every report — rather than emitting it only
to logs — preserves the full audit trail and unlocks straightforward cost
and latency analysis without re-querying the provider.

### Per-category confidence (#150)

The LLM also reports `HIGH | MEDIUM | LOW` confidence per category. This is
optional in the JSON schema (Jackson reads a missing field as
`Optional.empty()`) so reports written before #150 still deserialise. The
detail and comparator views surface confidence so a user can distinguish a
confident verdict from a guess.

### Source pluggability

`adapter.out.envoy` is unaware of source mechanics; the
`adapter.out.ingest` package contains `ManualPasteSource`, `UrlFetchSource`,
and `GreenhouseApiSource`, each implementing the `JobSource` outbound port.
A `JobSourceRequest` carries a discriminator (`type`) plus a payload and an
optional hint map (company / title / location) so callers can short-circuit
the LLM extractor when they already know the metadata.

### Inline UI and detail / compare / author views

Envoy ships a Tailwind-styled Thymeleaf UI (ADR-0019), not just an API:

| Route | Controller | Purpose |
|---|---|---|
| `GET /envoy` | `EnvoyPageController` | List recent score reports for the user's org, with filter strip (min score, recommendation) and inline ingest form (#143, #144, #146). |
| `POST /envoy` | `EnvoyPageController` | Inline ingest+score against the `default` rubric; on failure re-renders in place with `ingestError`. |
| `GET /envoy/reports/{id}` | `EnvoyPageController` | Detail view of one report — every category rationale, every flag rationale, confidence, usage (#145, #150, #161). |
| `GET /envoy/compare?ids=&rubric=` | `EnvoyComparatorController` | Side-by-side comparison of 2–5 reports under one rubric; rows highlight the highest-scoring cell and surface gap-from-apply-threshold (#159). |
| `GET /envoy/rubrics` | `RubricAuthorController` | List rubrics visible to the active org. |
| `GET /envoy/rubrics/{name}/edit` | `RubricAuthorController` | Form-based rubric authoring (no hand-written JSON) (#160). |
| `POST /envoy/rubrics/{name}` | `RubricAuthorController` | Submit a new rubric version; redirects to list with a flash message. |

The REST surface mirrors the UI: `/api/envoy/postings`, `/api/envoy/reports`,
`/api/envoy/rubrics` — with `OrganizationAccessService` enforcing per-org
access on every JSON entry point.

### Notifications on high scores (#148)

A `JobPostingScored` event with `Recommendation.APPLY_NOW` triggers
`HighScorePostingNotificationListener`, which fans out via the existing
`NotificationPort` to every member of the owning org who has not disabled
the new `HIGH_SCORE_POSTING` notification category (joining
`MAINTENANCE_DUE`, `WARRANTY_EXPIRING`, `SITE_UPDATES`). Lower
recommendations are silently dropped — users explicitly opted into Envoy by
ingesting a posting; they should not be paged for `CONSIDER`-tier results.

### Batch + auto rescore on rubric change (#147)

When a new rubric version is created, `RubricVersionCreated` is published
and `RubricChangeRescoreListener` re-scores every posting in the org against
the new version. The same fan-out is exposed manually via
`POST /api/envoy/postings/rescore` for ad-hoc re-evaluation (e.g. after a
model upgrade with no rubric change). Throttling is delegated to the
existing Resilience4j circuit breaker and retry on the `envoy-llm` instance
configured in `application.yml`; no new resilience config was introduced.
At personal scale this raw fan-out is acceptable; if the workload grows,
queued/chunked backpressure can be added without changing the listener's
contract.

## Consequences

### Good

- **Verdicts are reproducible.** The rubric is a versioned domain object,
  not a prompt template. Two runs against the same `(posting, rubric
  version)` produce the same arithmetic; only the LLM tier picks differ,
  and those are persisted as rationale strings for audit.
- **The LLM is contained.** Prompting and JSON parsing are confined to the
  outbound LLM adapter; the application layer's `ScoreAssembler` is fully
  unit-testable without the model. Score arithmetic, disqualifier
  short-circuits, and recommendation derivation cannot drift with a model
  upgrade.
- **History is durable.** Every score is a row, with token counts and
  latency, so cost/latency dashboards and "why did this posting score X six
  months ago?" both work without re-querying the provider.
- **Multi-rubric is cheap.** Comparing a posting under "principal-engineer"
  and "early-stage" rubrics is a single API call and a single comparator
  page — no manual fan-out in the client.
- **Notifications respect existing infrastructure.** `HIGH_SCORE_POSTING`
  joins the existing notification category enum and listener pattern; users
  can opt out per-category via `UserPreferences` exactly as they can for
  warranty and maintenance alerts.
- **Hexagonal boundaries hold.** The domain layer
  (`domain.model.envoy`, `domain.port.in.envoy`, `domain.port.out.envoy`)
  has zero LLM, HTTP, or framework dependencies — ArchUnit (ADR-0017)
  enforces this at test time alongside every other Majordomo service.

### Bad / accepted trade-offs

- **LLM cost and latency in the request path.** Inline ingest+score on
  `POST /envoy` blocks the user on a model call. Acceptable at personal
  scale; if it stops being acceptable, the inline path can be split
  (ingest synchronous, score asynchronous via the existing event system)
  without changing the domain.
- **Naive batch rescore fan-out.** `RubricChangeRescoreListener` and the
  manual `/rescore` endpoint loop sequentially through postings in-process,
  relying solely on the `envoy-llm` circuit breaker for throttling. Fine
  for the current workload; not a queue.
- **Reports keep stale rubric snapshots.** A `ScoreReport` references its
  `Rubric` by id and version but does not embed the rubric body. If a
  rubric row were ever hard-deleted (it shouldn't — we soft-delete by
  archive convention), historical reports would lose context. The
  `rubricVersion` is denormalised onto every report row to mitigate this.
- **`default` rubric is opinionated.** The seeded `default` rubric reflects
  one user's hiring priorities (compensation, remote, role scope, team
  signals, company stage, tech stack). Other orgs are expected to author
  their own; the `default` is a starting point, not a recommendation.
- **JSONB schema lives in domain types.** Rubric and report bodies are
  Jackson-mapped from JSONB columns. Adding a field requires either making
  it `Optional`/nullable (as `confidence` and `usage` were) or migrating
  the existing rows. We have explicitly chosen `Optional` over migration
  every time so far.

## References

- Issues closed by this design: #119 (foundational), #143 (inline ingest),
  #144 (row enrichment), #145 (detail page), #146 (filter strip), #147
  (batch + auto rescore), #148 (`APPLY_NOW` notifications), #149
  (multi-rubric), #150 (per-category confidence), #159 (comparator), #160
  (rubric author UI), #161 (usage/latency capture), #167 (usage/latency
  persistence).
- Related ADRs: ADR-0002 (service naming), ADR-0004 (hexagonal
  architecture), ADR-0011 (Postgres + Flyway), ADR-0017 (ArchUnit), ADR-0018
  (UUIDv7), ADR-0019 (Tailwind), ADR-0020 (audit logging — the
  `JobPostingScored` and `RubricVersionCreated` events flow through the
  existing audit listener for free).
