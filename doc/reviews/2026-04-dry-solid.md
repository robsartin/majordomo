# Code review: DRY, SOLID, design patterns

**Date:** 2026-04-28
**Scope:** `src/main/java/com/majordomo/**`
**Issue:** #178

## Methodology

Read-only structural review focused on the most-changed packages: `adapter/in/web/envoy`, `application/envoy`, `adapter/out/persistence/**`, `adapter/in/web/**`, top-level controllers. ArchUnit (ADR-0017) already enforces hexagonal layer boundaries; Checkstyle (ADR-0014) enforces unused imports, length, and visibility. This review targets the layer above those tools: duplicated logic, SRP smells, and missed design-pattern opportunities.

## Findings

Severity scale: **HIGH** = filed as ready issue; **MED** = backlog (low-risk refactor); **LOW** = note.

### HIGH

**[H1] `AuthContext` + `resolveContext` duplicated across 5 controllers.**
Files:
- `adapter/in/web/DashboardPageController.java:46–53`
- `adapter/in/web/envoy/EnvoyPageController.java:67–74, 364–371`
- `adapter/in/web/envoy/EnvoyComparatorController.java:93–94, 261–268`
- `adapter/in/web/envoy/RubricComparatorController.java:34, 95–102`
- `adapter/in/web/envoy/RubricAuthorController.java:287–294`

Each controller declares an identical `private record AuthContext(User user, UUID organizationId)` plus a 6-line `resolveContext` that pulls the user, looks up memberships, and returns the *first* org. This is both DRY (same code ×5) and SRP (controllers know about the membership-resolution policy). Filed as **#191** — extract a `CurrentOrganizationResolver` Spring component.

**[H2] Conversion-stat query lives in a Thymeleaf controller.**
File: `adapter/in/web/envoy/EnvoyPageController.java:225–235`

`renderEnvoyPage` runs a second `reports.query(...)` and an N+1 `findById` loop just to count "X of Y APPLY_NOW postings applied". This is read-model logic in the inbound adapter. It belongs in an application service (alongside `RecentApplyNowQueryService`), ideally cached. Filed as **#192**.

**[H3] Metrics recording duplicated in services.**
Files:
- `application/envoy/JobScorerService.java:138–167` (`recordTimer`, `recordTokenUsage`)
- `application/envoy/PostingConversionService.java:60–69, 80–89` (counter builders inline)

Both build `Counter.builder(...).description(...).tag(org, ...).tag(...).register(meterRegistry).increment()` directly. Tag conventions, registration cost, and naming will drift as more services emit metrics. Filed as **#193** — extract a small `EnvoyMetrics` (or generic `Metrics`) helper that owns metric names, descriptions, and tag keys.

### MED — refactor backlog

**[M1] Mapper boilerplate (17 mapper classes).**
Each `*Mapper` is a final class with a private constructor and static `toEntity`/`toDomain` methods. A consistent shape, plus duplicated try/catch on JSON column serialization (`extracted` in `JobPostingMapper`, similar in `ScoreReportMapper`). Worth evaluating MapStruct (or a small `JsonColumnCodec` utility for the JSONB cases) before the count grows. Not urgent — these are stable and well-tested.

**[M2] `JobScorerService` mixes orchestration with observability.**
`runOne` interleaves "load + score + assemble + persist + publish event" with three observability concerns (latency timer, token counters, model-id tagging). Extract a tiny `LlmCallObserver` collaborator, or emit a single `LlmCallCompleted` domain event consumed by an observability listener (like `AuditEventListener`). Lowers the SRP weight on the scorer.

**[M3] `ScoreReportRepository.query` has a 5-parameter signature.**
`(organizationId, minFinalScore, recommendation, cursor, limit)`. As filters grow (e.g. "applied / dismissed only"), this gets uglier. Specification or filter object: `ScoreReportFilter` record with optional fields, plus a method `query(orgId, ScoreReportFilter, cursor, limit)`. Same applies to envoy's pagination ports more broadly.

**[M4] `Recommendation` derivation lives in `ScoreAssembler` rather than the enum.**
`Recommendation.fromScore(int finalScore, Thresholds thresholds)` would be a one-liner that lives next to the enum and is independently testable. Strategy-flavored, but it's just a static factory — keeps the assembler thinner.

### LOW — notes

**[L1] `CacheEvictionListener` is one switch on event type away from a registry.**
Currently two `@EventListener` methods, each evicting one cache. As more events arrive this will grow linearly. A small `Map<Class<?>, Set<String>>` registered in config would replace the listener entirely. Not worth doing yet — the file is still small.

**[L2] `JobSourceRequest` is constructed inline in `EnvoyPageController.submitIngest` from request params + a hint map.**
A small mapping helper or DTO + `@ModelAttribute` would clean up the controller body. Minor.

**[L3] Five `*PageController` Thymeleaf controllers each declare `private static final int DEFAULT_LIMIT`.**
Different defaults are intentional per page, but the constant placement is consistent enough that a `@ConfigurationProperties` block would surface these in `application.yml` and make tuning easier. Premature for now.

**[L4] `JobPosting` is mutable (POJO with setters), `Rubric` and `ScoreReport` are records.**
Stylistic split that reflects history more than design. The CLAUDE.md note explains it as matching `Property`/`Contact` convention, which is the right call — flagging only because it's worth noting in the design discussion when refactoring envoy.

## Out of scope

- Architectural redesign at hexagonal-boundary level (covered by ArchUnit and the ADRs).
- Implementing any of the listed refactors — those are tracked in #191, #192, #193.
- Test code review (covered by #177's coverage audit and its follow-ups).
