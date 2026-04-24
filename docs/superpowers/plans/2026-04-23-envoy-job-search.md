# Envoy (Job Search) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `envoy` — a majordomo service that scores job postings against a versioned rubric using an LLM for fuzzy interpretation and Java for deterministic orchestration, validation, and persistence.

**Architecture:** Hexagonal service inside the existing majordomo monolith, package-separated as `com.majordomo.{layer}.envoy` (mirrors `steward`, `herald`, `ledger`). Java records model the immutable rubric/score value objects; a mutable POJO models `JobPosting`. The LLM picks tiers from a rubric you define; Java validates the response, applies disqualifiers/flags, and persists `ScoreReport` rows that reference the exact rubric version used. Pluggable `JobSource` implementations (manual paste, URL fetch, job-board APIs) are Spring-discovered. No separate deployable — everything runs inside the existing Spring Boot app.

**Tech Stack:** Java 25, Spring Boot 3.5, PostgreSQL 18 (JSONB via `@JdbcTypeCode(SqlTypes.JSON)`), Flyway, Spring Security, Resilience4j (wrapped around the LLM call), **official Anthropic Java SDK** (`com.anthropic:anthropic-java`), Jackson, ArchUnit (existing rules auto-apply). Default model: `claude-sonnet-4-6`.

---

## Scope & Non-Goals

**In scope (Phases 1-4):**
- Domain records, enums, value objects
- LLM scoring orchestrator with deterministic validation/assembly
- Anthropic Messages API adapter using `RestClient` (no SDK dep)
- Manual-paste ingestion + URL fetch + Greenhouse API source
- Versioned rubric persistence
- Full REST API (`/api/envoy/...`)
- Domain events + audit listener wiring

**Out of scope (Phase 5 — defer to a separate plan once Phase 4 ships):**
- Batch backlog rescoring, notification wiring for high-scoring postings, UI/CLI browser, multi-rubric scoring, re-score-on-rubric-change automation, LLM confidence-per-category.

---

## File Structure

New files under `src/main/java/com/majordomo/`:

```
domain/
  model/envoy/
    JobPosting.java                    Mutable POJO (matches Property pattern)
    JobSourceRequest.java              Record
    Rubric.java                        Record
    Category.java                      Record
    Tier.java                          Record
    Disqualifier.java                  Record
    Flag.java                          Record
    Thresholds.java                    Record
    ScoreReport.java                   Record
    CategoryScore.java                 Record
    FlagHit.java                       Record
    Recommendation.java                Enum
    LlmScoreResponse.java              Record (LLM output contract)
  model/event/
    JobPostingIngested.java            Record (domain event)
    JobPostingScored.java              Record (domain event)
  port/in/envoy/
    IngestJobPostingUseCase.java
    ScoreJobPostingUseCase.java
    ManageRubricUseCase.java
    QueryScoreReportsUseCase.java
  port/out/envoy/
    JobPostingRepository.java
    ScoreReportRepository.java
    RubricRepository.java
    LlmScoringPort.java
    LlmExtractionPort.java
application/envoy/
  JobScorer.java                       Implements ScoreJobPostingUseCase
  JobIngestionService.java             Implements IngestJobPostingUseCase (routes to JobSource)
  RubricService.java                   Implements ManageRubricUseCase
  ScoreReportQueryService.java         Implements QueryScoreReportsUseCase
  PromptBuilder.java                   Builds the Anthropic prompt from rubric+posting
  ScoreAssembler.java                  Validates LLM response, applies deterministic math
adapter/
  in/web/envoy/
    PostingController.java             POST /api/envoy/postings, POST .../{id}/score
    ReportController.java              GET /api/envoy/reports
    RubricController.java              PUT /api/envoy/rubrics/{name}
    dto/                               Request/response DTOs for the API
  in/event/
    (extends existing AuditEventListener — no new file)
  out/persistence/envoy/
    RubricEntity.java + JpaRubricRepository + RubricMapper + RubricRepositoryAdapter
    JobPostingEntity.java + JpaJobPostingRepository + JobPostingMapper + JobPostingRepositoryAdapter
    ScoreReportEntity.java + JpaScoreReportRepository + ScoreReportMapper + ScoreReportRepositoryAdapter
  out/llm/
    AnthropicLlmScoringAdapter.java    Implements LlmScoringPort
    AnthropicLlmExtractionAdapter.java Implements LlmExtractionPort
    AnthropicRestClient.java           Thin RestClient wrapper (shared)
  out/ingest/
    JobSource.java                     Interface (lives here because all impls are out-adapters)
    ManualPasteSource.java
    UrlFetchSource.java
    GreenhouseApiSource.java
```

Resources:

```
src/main/resources/db/migration/
  V14__envoy_schema.sql                Tables: rubric, job_posting, score_report
  V15__envoy_seed_default_rubric.sql   Seed the first rubric row
src/main/resources/envoy/
  default-rubric.json                  Rubric body loaded by the seed migration
```

Tests (mirror structure under `src/test/java/com/majordomo/`).

---

## Conventions Recap (apply to every task)

- Javadoc on every public class and public method (Checkstyle enforced at `./mvnw validate`). Getters/setters exempt.
- UUIDs only via `UuidFactory.newId()` — never `UUID.randomUUID()`.
- Domain models (under `domain/`) must not import `org.springframework.*`, `jakarta.persistence.*`, or `org.hibernate.*` (ArchUnit will fail the build).
- Mappers are package-private `final` classes with a private constructor and static methods.
- Repository adapters use `@Repository`, live in `adapter/out/persistence/envoy/`, implement a domain outbound port, delegate to a `Jpa*Repository`.
- Tests: use test slices per ADR-0021 — `@WebMvcTest` for controllers, `@DataJpaTest` for repository adapters, plain Mockito for application services. Reserve `@IntegrationTest` (meta-annotation at `src/test/java/com/majordomo/IntegrationTest.java`) for end-to-end smoke tests.
- Commits: one commit per task (TDD: test → implement → pass → commit).
- Commit style follows recent history (`git log` shows short lowercase imperatives, e.g. `add ...`, `fix ...`). No Co-Authored-By line unless user asks.

---

## Multi-Tenancy Conventions

Envoy scopes data by `organizationId` following the same pattern as `Property`/`Contact`/`ServiceRecord`. Every task below adopts these rules:

**Data:**
- `JobPosting` — has a non-null `organizationId: UUID` field.
- `ScoreReport` — has a non-null `organizationId: UUID` field (denormalized from the posting for query performance).
- `Rubric` — has a nullable `organizationId: UUID` field. **`null` means "system default" template**, visible to every org; non-null rubrics are org-specific.

**Lookup rule for rubrics:** `RubricRepository.findActiveByName(name, organizationId)` returns the highest-version rubric for `(name, organizationId)` if one exists; otherwise falls back to the highest-version rubric for `(name, organizationId IS NULL)`. This lets the seeded `default` rubric serve every org until that org edits it (which creates an org-specific version).

**Ports:**
- Every outbound repository method that reads or dedupes data takes a `UUID organizationId` parameter.
- Every inbound use-case method takes a `UUID organizationId` parameter.

**Controllers:**
- Every REST endpoint takes `@RequestParam UUID organizationId`.
- The first line of every handler body is `organizationAccessService.verifyAccess(organizationId)` — identical to `PropertyController`.

**Events:**
- `JobPostingIngested` and `JobPostingScored` both carry `organizationId`.

**Tests:**
- Each test fixture declares a stable `UUID orgId = UuidFactory.newId();` in setup and passes it everywhere. Controller tests additionally `@MockitoBean` the `OrganizationAccessService` and default-accept via `doNothing().when(...).verifyAccess(any())`.

Apply these rules to every task — the code blocks below are updated to match. When a task's example test omits `organizationId` from a constructor call, add it; the compile error will make the omission obvious.

---

## Phase 1 — Scoring Vertical Slice (hardcoded rubric, no DB)

**Goal:** Score a pasted job posting end-to-end via the Anthropic Messages API, printing a `ScoreReport`. No persistence, no REST. Validates the LLM contract and scoring math before committing to a schema.

### Task 1: Create feature branch

**Files:** none.

- [ ] **Step 1: Branch from `main`**

```bash
git checkout main
git pull --ff-only
git checkout -b feat/envoy
```

- [ ] **Step 2: Verify clean build on branch**

Run: `./mvnw verify`
Expected: BUILD SUCCESS. If it fails on `main`, stop and flag.

---

### Task 2: Add value-object records for rubric structure

**Files:**
- Create: `src/main/java/com/majordomo/domain/model/envoy/Tier.java`
- Create: `src/main/java/com/majordomo/domain/model/envoy/Disqualifier.java`
- Create: `src/main/java/com/majordomo/domain/model/envoy/Flag.java`
- Create: `src/main/java/com/majordomo/domain/model/envoy/Thresholds.java`
- Test: `src/test/java/com/majordomo/domain/model/envoy/RubricValueObjectsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.domain.model.envoy;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RubricValueObjectsTest {

    @Test
    void tier_exposesLabelPointsCriteria() {
        var t = new Tier("Excellent", 20, "Salary > $250k base");
        assertThat(t.label()).isEqualTo("Excellent");
        assertThat(t.points()).isEqualTo(20);
        assertThat(t.criteria()).isEqualTo("Salary > $250k base");
    }

    @Test
    void disqualifier_exposesKeyAndDescription() {
        var d = new Disqualifier("ON_SITE_ONLY", "Role requires on-site work");
        assertThat(d.key()).isEqualTo("ON_SITE_ONLY");
        assertThat(d.description()).isEqualTo("Role requires on-site work");
    }

    @Test
    void flag_exposesKeyDescriptionPenalty() {
        var f = new Flag("AT_WILL_LANGUAGE", "Aggressive at-will language", 5);
        assertThat(f.penalty()).isEqualTo(5);
    }

    @Test
    void thresholds_orderedHighToLow() {
        var th = new Thresholds(85, 70, 50);
        assertThat(th.applyImmediately()).isGreaterThan(th.apply());
        assertThat(th.apply()).isGreaterThan(th.considerOnly());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=RubricValueObjectsTest test`
Expected: COMPILATION FAILURE — types do not exist.

- [ ] **Step 3: Create the four record files**

`Tier.java`:
```java
package com.majordomo.domain.model.envoy;

/**
 * A scoring tier within a {@link Category}. Tiers are ordered within a category
 * from highest to lowest points; the LLM picks exactly one tier per category.
 *
 * @param label    human-readable tier name (e.g. "Excellent", "Acceptable")
 * @param points   points awarded when this tier is selected
 * @param criteria description of when this tier applies — read by the LLM
 */
public record Tier(String label, int points, String criteria) { }
```

`Disqualifier.java`:
```java
package com.majordomo.domain.model.envoy;

/**
 * A hard disqualifier. If the LLM finds evidence of a disqualifier in the posting,
 * the recommendation is automatically {@code SKIP} regardless of raw score.
 *
 * @param key         stable identifier (e.g. "ON_SITE_ONLY"); referenced in score reports
 * @param description natural-language criteria read by the LLM
 */
public record Disqualifier(String key, String description) { }
```

`Flag.java`:
```java
package com.majordomo.domain.model.envoy;

/**
 * A soft flag that subtracts points from the raw score when hit. Unlike a
 * {@link Disqualifier}, a flag does not force a SKIP; it only reduces the score.
 *
 * @param key         stable identifier (e.g. "AT_WILL_LANGUAGE")
 * @param description natural-language criteria read by the LLM
 * @param penalty     points subtracted when this flag is hit (positive number)
 */
public record Flag(String key, String description, int penalty) { }
```

`Thresholds.java`:
```java
package com.majordomo.domain.model.envoy;

/**
 * Score thresholds used to derive a {@link Recommendation} from the final score.
 * Values are score cutoffs and must satisfy
 * {@code applyImmediately >= apply >= considerOnly}.
 *
 * @param applyImmediately minimum score for {@code APPLY_NOW}
 * @param apply            minimum score for {@code APPLY}
 * @param considerOnly     minimum score for {@code CONSIDER}; below this is {@code SKIP}
 */
public record Thresholds(int applyImmediately, int apply, int considerOnly) { }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -Dtest=RubricValueObjectsTest test`
Expected: 4 tests pass.

- [ ] **Step 5: Verify Checkstyle**

Run: `./mvnw validate`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/majordomo/domain/model/envoy/ \
        src/test/java/com/majordomo/domain/model/envoy/
git commit -m "add envoy rubric value-object records"
```

---

### Task 3: Add `Category` and `Rubric` records

**Files:**
- Create: `src/main/java/com/majordomo/domain/model/envoy/Category.java`
- Create: `src/main/java/com/majordomo/domain/model/envoy/Rubric.java`
- Test: `src/test/java/com/majordomo/domain/model/envoy/RubricTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.domain.model.envoy;

import com.majordomo.domain.model.UuidFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RubricTest {

    @Test
    void rubric_holdsAllFields() {
        var tier = new Tier("Good", 15, "Salary in range");
        var cat = new Category("compensation", "Pay & equity", 20, List.of(tier));
        var rubric = new Rubric(
                UuidFactory.newId(),
                1,
                "default",
                List.of(new Disqualifier("ON_SITE", "on-site required")),
                List.of(cat),
                List.of(new Flag("AT_WILL", "aggressive at-will", 5)),
                new Thresholds(85, 70, 50),
                Instant.now());

        assertThat(rubric.version()).isEqualTo(1);
        assertThat(rubric.categories()).hasSize(1);
        assertThat(rubric.categories().get(0).tiers()).hasSize(1);
        assertThat(rubric.categories().get(0).maxPoints()).isEqualTo(20);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=RubricTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create the records**

`Category.java`:
```java
package com.majordomo.domain.model.envoy;

import java.util.List;

/**
 * A scoring category. Each category has a cap ({@code maxPoints}) and an ordered
 * list of {@link Tier}s the LLM chooses from. The highest-points tier should be
 * listed first for readability.
 *
 * @param key         stable identifier (e.g. "compensation")
 * @param description natural-language description read by the LLM
 * @param maxPoints   upper bound on the points any tier in this category awards
 * @param tiers       ordered tiers (highest-scoring first)
 */
public record Category(String key, String description, int maxPoints, List<Tier> tiers) { }
```

`Rubric.java`:
```java
package com.majordomo.domain.model.envoy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A versioned scoring rubric. Rubrics are immutable once persisted; edits produce
 * a new row with {@code version + 1} and a new {@code effectiveFrom}. Score
 * reports reference a specific rubric version so historical scores remain
 * reproducible.
 *
 * <p>{@code organizationId} is {@link Optional#empty()} for the seeded <strong>system
 * default</strong> template (visible to every org). An org-specific rubric has a
 * present {@code organizationId} and shadows the system default when
 * {@code findActiveByName} is called.</p>
 *
 * @param id             UUIDv7 assigned at persist time
 * @param organizationId empty for the system default; present for org-specific rubrics
 * @param version        monotonically increasing per {@code (organizationId, name)}
 * @param name           logical rubric name (e.g. "default")
 * @param disqualifiers  hard disqualifiers — any hit forces recommendation SKIP
 * @param categories     scoring categories the LLM evaluates
 * @param flags          soft penalties applied after category scoring
 * @param thresholds     score cutoffs that map raw score to {@link Recommendation}
 * @param effectiveFrom  timestamp this version became active
 */
public record Rubric(
        UUID id,
        Optional<UUID> organizationId,
        int version,
        String name,
        List<Disqualifier> disqualifiers,
        List<Category> categories,
        List<Flag> flags,
        Thresholds thresholds,
        Instant effectiveFrom
) { }
```

> Update the test in Step 1 to pass `Optional.of(orgId)` (or `Optional.empty()` for system-default fixtures) as the second arg when constructing `Rubric`. The compiler will point to every call site that needs updating — this record constructor change ripples through every subsequent task.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -Dtest=RubricTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/majordomo/domain/model/envoy/Category.java \
        src/main/java/com/majordomo/domain/model/envoy/Rubric.java \
        src/test/java/com/majordomo/domain/model/envoy/RubricTest.java
git commit -m "add envoy Category and Rubric records"
```

---

### Task 4: Add `Recommendation` enum + `ScoreReport` records

**Files:**
- Create: `src/main/java/com/majordomo/domain/model/envoy/Recommendation.java`
- Create: `src/main/java/com/majordomo/domain/model/envoy/CategoryScore.java`
- Create: `src/main/java/com/majordomo/domain/model/envoy/FlagHit.java`
- Create: `src/main/java/com/majordomo/domain/model/envoy/ScoreReport.java`
- Test: `src/test/java/com/majordomo/domain/model/envoy/ScoreReportTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.domain.model.envoy;

import com.majordomo.domain.model.UuidFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreReportTest {

    @Test
    void scoreReport_capturesAllAuditFields() {
        var cs = new CategoryScore("compensation", 15, "Good", "salary band listed at $200-250k");
        var fh = new FlagHit("AT_WILL", 5, "explicit at-will clause in posting");
        var report = new ScoreReport(
                UuidFactory.newId(),
                UuidFactory.newId(),
                UuidFactory.newId(),
                3,
                Optional.empty(),
                List.of(cs),
                List.of(fh),
                15,
                10,
                Recommendation.CONSIDER,
                "claude-sonnet-4-6",
                Instant.now());

        assertThat(report.rawScore()).isEqualTo(15);
        assertThat(report.finalScore()).isEqualTo(10);
        assertThat(report.recommendation()).isEqualTo(Recommendation.CONSIDER);
        assertThat(report.disqualifiedBy()).isEmpty();
        assertThat(report.categoryScores()).hasSize(1);
    }

    @Test
    void recommendation_hasFourValues() {
        assertThat(Recommendation.values()).containsExactlyInAnyOrder(
                Recommendation.APPLY_NOW, Recommendation.APPLY,
                Recommendation.CONSIDER, Recommendation.SKIP);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=ScoreReportTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `Recommendation.java`**

```java
package com.majordomo.domain.model.envoy;

/**
 * Final recommendation derived from the score and rubric thresholds.
 */
public enum Recommendation {
    /** Score meets or exceeds the applyImmediately threshold. */
    APPLY_NOW,
    /** Score meets the apply threshold but not applyImmediately. */
    APPLY,
    /** Score meets the considerOnly threshold but not apply. */
    CONSIDER,
    /** Score is below considerOnly, or a disqualifier was hit. */
    SKIP
}
```

- [ ] **Step 4: Create `CategoryScore.java`**

```java
package com.majordomo.domain.model.envoy;

/**
 * The LLM's verdict for a single category. The rationale is preserved verbatim
 * for audit so surprising scores can be explained after the fact.
 *
 * @param categoryKey the {@link Category#key()} this score applies to
 * @param points      points awarded (matches the selected tier's points)
 * @param tierLabel   label of the {@link Tier} the LLM selected
 * @param rationale   LLM's free-text reasoning for the tier selection
 */
public record CategoryScore(String categoryKey, int points, String tierLabel, String rationale) { }
```

- [ ] **Step 5: Create `FlagHit.java`**

```java
package com.majordomo.domain.model.envoy;

/**
 * A flag the LLM judged to have fired on a posting, with its penalty.
 *
 * @param flagKey   the {@link Flag#key()} that fired
 * @param penalty   points subtracted from the raw score (mirrors {@link Flag#penalty()})
 * @param rationale LLM's reasoning for raising this flag
 */
public record FlagHit(String flagKey, int penalty, String rationale) { }
```

- [ ] **Step 6: Create `ScoreReport.java`**

```java
package com.majordomo.domain.model.envoy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable scoring output. One {@code ScoreReport} is produced per (posting, rubric-version)
 * pair. All LLM rationale is preserved so historical decisions remain auditable after the
 * rubric evolves.
 *
 * @param id              UUIDv7 assigned at persist time
 * @param organizationId  the owning org (denormalized from the posting for query speed)
 * @param postingId       the {@link JobPosting} scored
 * @param rubricId        the {@link Rubric} used
 * @param rubricVersion   denormalized for easy querying even if rubric rows are retired
 * @param disqualifiedBy  present if a disqualifier fired (forces recommendation SKIP)
 * @param categoryScores  one entry per category in the rubric
 * @param flagHits        any flags the LLM raised
 * @param rawScore        sum of category points (before flag penalties)
 * @param finalScore      rawScore minus sum of flag penalties (floored at 0)
 * @param recommendation  derived from finalScore and rubric thresholds
 * @param llmModel        model identifier used (e.g. "claude-sonnet-4-6") for reproducibility
 * @param scoredAt        timestamp the scoring completed
 */
public record ScoreReport(
        UUID id,
        UUID organizationId,
        UUID postingId,
        UUID rubricId,
        int rubricVersion,
        Optional<Disqualifier> disqualifiedBy,
        List<CategoryScore> categoryScores,
        List<FlagHit> flagHits,
        int rawScore,
        int finalScore,
        Recommendation recommendation,
        String llmModel,
        Instant scoredAt
) { }
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./mvnw -Dtest=ScoreReportTest test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/majordomo/domain/model/envoy/ \
        src/test/java/com/majordomo/domain/model/envoy/ScoreReportTest.java
git commit -m "add envoy ScoreReport, CategoryScore, FlagHit, Recommendation"
```

---

### Task 5: Add `JobPosting` POJO and `JobSourceRequest` record

**Files:**
- Create: `src/main/java/com/majordomo/domain/model/envoy/JobPosting.java`
- Create: `src/main/java/com/majordomo/domain/model/envoy/JobSourceRequest.java`
- Test: `src/test/java/com/majordomo/domain/model/envoy/JobPostingTest.java`

`JobPosting` is a mutable POJO following the `Property` pattern (ADR-precedent — primary entities that get persisted, fetched, and re-fetched are POJOs with getters/setters). Value objects remain records.

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.domain.model.envoy;

import com.majordomo.domain.model.UuidFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingTest {

    @Test
    void jobPosting_roundTripsAllFields() {
        var p = new JobPosting();
        p.setId(UuidFactory.newId());
        p.setSource("manual");
        p.setExternalId("abc-123");
        p.setCompany("Acme");
        p.setTitle("Senior Engineer");
        p.setLocation("Remote (US)");
        p.setRawText("We are hiring...");
        p.setExtracted(Map.of("salary", "$200k-$250k"));
        p.setFetchedAt(Instant.parse("2026-04-23T12:00:00Z"));

        assertThat(p.getCompany()).isEqualTo("Acme");
        assertThat(p.getExtracted()).containsEntry("salary", "$200k-$250k");
        assertThat(p.getFetchedAt()).isEqualTo(Instant.parse("2026-04-23T12:00:00Z"));
    }

    @Test
    void jobSourceRequest_holdsTypePayloadHints() {
        var req = new JobSourceRequest("url", "https://jobs.example.com/1", Map.of("company", "Acme"));
        assertThat(req.type()).isEqualTo("url");
        assertThat(req.hints()).containsEntry("company", "Acme");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=JobPostingTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `JobPosting.java`**

```java
package com.majordomo.domain.model.envoy;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A job posting ingested from a {@code JobSource}. Primary entity — gets persisted,
 * re-fetched, and potentially re-scored as rubrics evolve. Mutable to match the
 * {@code Property}/{@code Contact} convention used elsewhere in majordomo.
 *
 * <p>{@code rawText} holds the full posting body as provided by the source;
 * {@code extracted} holds any structured fields the ingester pulled out (salary,
 * equity, team size, etc.).</p>
 */
public class JobPosting {

    private UUID id;
    private UUID organizationId;
    @NotBlank
    private String source;
    private String externalId;
    private String company;
    private String title;
    private String location;
    @NotBlank
    private String rawText;
    private Map<String, String> extracted;
    private Instant fetchedAt;
    private Instant archivedAt;

    public JobPosting() { }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public Map<String, String> getExtracted() { return extracted; }
    public void setExtracted(Map<String, String> extracted) { this.extracted = extracted; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
```

- [ ] **Step 4: Create `JobSourceRequest.java`**

```java
package com.majordomo.domain.model.envoy;

import java.util.Map;

/**
 * A request to ingest a job posting. Routed by {@code JobIngestionService} to the
 * first {@code JobSource} whose {@code supports(...)} returns true.
 *
 * @param type    source discriminator (e.g. "manual", "url", "greenhouse")
 * @param payload the primary input — raw posting text, a URL, or a source-specific job ID
 * @param hints   optional out-of-band data (e.g. company name the caller already knows)
 */
public record JobSourceRequest(String type, String payload, Map<String, String> hints) { }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -Dtest=JobPostingTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/majordomo/domain/model/envoy/JobPosting.java \
        src/main/java/com/majordomo/domain/model/envoy/JobSourceRequest.java \
        src/test/java/com/majordomo/domain/model/envoy/JobPostingTest.java
git commit -m "add envoy JobPosting and JobSourceRequest"
```

---

### Task 6: Define the LLM output contract `LlmScoreResponse`

**Files:**
- Create: `src/main/java/com/majordomo/domain/model/envoy/LlmScoreResponse.java`
- Test: `src/test/java/com/majordomo/domain/model/envoy/LlmScoreResponseTest.java`

The LLM returns **decisions**, not points. Java looks up points from the rubric. The LLM returns: a list of category verdicts (each a tier selection with rationale), a list of flag hits (key + rationale), and an optional disqualifier key. This keeps the LLM from inventing numeric values that bypass the rubric's cap.

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.domain.model.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LlmScoreResponseTest {

    @Test
    void deserialisesFromCanonicalJson() throws Exception {
        String json = """
                {
                  "disqualifierKey": null,
                  "categoryVerdicts": [
                    {"categoryKey": "compensation", "tierLabel": "Good",
                     "rationale": "Posting lists $200-250k base."}
                  ],
                  "flagHits": [
                    {"flagKey": "AT_WILL", "rationale": "explicit at-will clause"}
                  ]
                }
                """;
        var mapper = new ObjectMapper();
        var resp = mapper.readValue(json, LlmScoreResponse.class);

        assertThat(resp.disqualifierKey()).isEmpty();
        assertThat(resp.categoryVerdicts()).hasSize(1);
        assertThat(resp.categoryVerdicts().get(0).tierLabel()).isEqualTo("Good");
        assertThat(resp.flagHits()).hasSize(1);
    }

    @Test
    void deserialisesWithDisqualifier() throws Exception {
        String json = """
                {
                  "disqualifierKey": "ON_SITE_ONLY",
                  "categoryVerdicts": [],
                  "flagHits": []
                }
                """;
        var resp = new ObjectMapper().readValue(json, LlmScoreResponse.class);
        assertThat(resp.disqualifierKey()).contains("ON_SITE_ONLY");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=LlmScoreResponseTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `LlmScoreResponse.java`**

```java
package com.majordomo.domain.model.envoy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Raw LLM output contract. The LLM returns tier <em>selections</em> and flag
 * <em>findings</em>; Java looks up the actual point values from the rubric.
 * This prevents the LLM from inventing point values that exceed category caps.
 *
 * @param disqualifierKey  present iff a disqualifier fired; must match a
 *                         {@link Disqualifier#key()} in the active rubric
 * @param categoryVerdicts one entry per category in the rubric (LLM must cover all)
 * @param flagHits         zero or more flags the LLM judged to have fired
 */
public record LlmScoreResponse(
        Optional<String> disqualifierKey,
        List<CategoryVerdict> categoryVerdicts,
        List<FlagFinding> flagHits
) {
    /**
     * @param categoryKey matches {@link Category#key()} in the rubric
     * @param tierLabel   matches a {@link Tier#label()} within that category
     * @param rationale   free text explaining the selection
     */
    public record CategoryVerdict(String categoryKey, String tierLabel, String rationale) { }

    /**
     * @param flagKey   matches {@link Flag#key()} in the rubric
     * @param rationale free text explaining why the flag fired
     */
    public record FlagFinding(String flagKey, String rationale) { }

    /** Jackson creator so nullable {@code disqualifierKey} deserialises to {@code Optional.empty()}. */
    @JsonCreator
    public static LlmScoreResponse of(
            @JsonProperty("disqualifierKey") String disqualifierKey,
            @JsonProperty("categoryVerdicts") List<CategoryVerdict> categoryVerdicts,
            @JsonProperty("flagHits") List<FlagFinding> flagHits) {
        return new LlmScoreResponse(
                Optional.ofNullable(disqualifierKey),
                categoryVerdicts == null ? List.of() : categoryVerdicts,
                flagHits == null ? List.of() : flagHits);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -Dtest=LlmScoreResponseTest test`
Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/majordomo/domain/model/envoy/LlmScoreResponse.java \
        src/test/java/com/majordomo/domain/model/envoy/LlmScoreResponseTest.java
git commit -m "add envoy LlmScoreResponse contract"
```

---

### Task 7: Outbound ports for repositories and LLM

**Files:**
- Create: `src/main/java/com/majordomo/domain/port/out/envoy/RubricRepository.java`
- Create: `src/main/java/com/majordomo/domain/port/out/envoy/JobPostingRepository.java`
- Create: `src/main/java/com/majordomo/domain/port/out/envoy/ScoreReportRepository.java`
- Create: `src/main/java/com/majordomo/domain/port/out/envoy/LlmScoringPort.java`

No test file for this task — interfaces alone. Tests arrive with Task 8.

- [ ] **Step 1: Create `RubricRepository.java`**

```java
package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.envoy.Rubric;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for rubric persistence. Rubrics are append-only; "update" means
 * "insert a new version with {@code version + 1}".
 */
public interface RubricRepository {

    /**
     * Returns the active rubric for {@code (organizationId, name)}: the highest-version
     * rubric owned by {@code organizationId} if one exists, otherwise the highest-version
     * system-default rubric ({@code organization_id IS NULL}) for that name. Empty if
     * neither exists.
     */
    Optional<Rubric> findActiveByName(String name, UUID organizationId);

    /** Finds a rubric by primary key (no org scoping — use for internal lookups only). */
    Optional<Rubric> findById(UUID id);

    /**
     * Lists all versions for {@code (organizationId, name)}, ordered by version ascending.
     * Does not include system-default versions.
     */
    List<Rubric> findAllVersionsByName(String name, UUID organizationId);

    /** Persists a rubric. Caller is responsible for setting id, version, and effectiveFrom. */
    Rubric save(Rubric rubric);
}
```

- [ ] **Step 2: Create `JobPostingRepository.java`**

```java
package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.envoy.JobPosting;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for job-posting persistence.
 */
public interface JobPostingRepository {

    /** Persists a new posting or updates an existing one by id. */
    JobPosting save(JobPosting posting);

    /** Finds a posting by id, scoped to an organization. */
    Optional<JobPosting> findById(UUID id, UUID organizationId);

    /**
     * Finds a posting by source + external id within an organization. Used to
     * deduplicate re-ingestion from the same upstream job board per org.
     */
    Optional<JobPosting> findBySourceAndExternalId(String source, String externalId, UUID organizationId);
}
```

- [ ] **Step 3: Create `ScoreReportRepository.java`**

```java
package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for score report persistence and querying.
 */
public interface ScoreReportRepository {

    /** Persists a new score report. Reports are immutable — overwriting is a misuse. */
    ScoreReport save(ScoreReport report);

    /** Finds a report by id, scoped to an organization. */
    Optional<ScoreReport> findById(UUID id, UUID organizationId);

    /**
     * Cursor-paginated query over reports within an organization, with optional
     * secondary filters. Null filter values mean "no filter on that field".
     *
     * @param organizationId required — reports are always org-scoped
     * @param minFinalScore  include only reports with finalScore >= this (null = no minimum)
     * @param recommendation include only reports with this recommendation (null = any)
     * @param cursor         next-page cursor (null = first page)
     * @param limit          clamped to [1, 100] by the caller; repository must honour limit+1
     */
    Page<ScoreReport> query(UUID organizationId, Integer minFinalScore,
                            Recommendation recommendation, UUID cursor, int limit);
}
```

- [ ] **Step 4: Create `LlmScoringPort.java`**

```java
package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Rubric;

/**
 * Outbound port for LLM-driven posting scoring. Implementations send the rubric +
 * posting to an LLM and return a parsed, structured response. Implementations must
 * validate the response is well-formed JSON matching {@link LlmScoreResponse} —
 * deep rubric validation (tier labels, disqualifier keys) is performed upstream
 * by {@code ScoreAssembler}.
 */
public interface LlmScoringPort {

    /**
     * Scores a posting against a rubric.
     *
     * @param posting the posting to score
     * @param rubric  the active rubric
     * @return the LLM's structured verdict
     * @throws com.majordomo.application.envoy.LlmScoringException if the LLM call
     *         fails or returns unparseable output
     */
    LlmScoreResponse score(JobPosting posting, Rubric rubric);

    /** Model identifier recorded on score reports for reproducibility. */
    String modelId();
}
```

- [ ] **Step 5: Verify build**

Run: `./mvnw validate compile`
Expected: BUILD SUCCESS. (The referenced `LlmScoringException` will be created in Task 8.)

> If the compile fails because `LlmScoringException` is missing, that is expected here — continue to Task 8 without committing. Otherwise, commit now.

- [ ] **Step 6: Stage only (commit together with Task 8)**

```bash
git add src/main/java/com/majordomo/domain/port/out/envoy/
```

---

### Task 8: Build `ScoreAssembler` — deterministic validation and math

**Files:**
- Create: `src/main/java/com/majordomo/application/envoy/LlmScoringException.java`
- Create: `src/main/java/com/majordomo/application/envoy/ScoreAssembler.java`
- Test: `src/test/java/com/majordomo/application/envoy/ScoreAssemblerTest.java`

`ScoreAssembler` is the deterministic core. It takes an `LlmScoreResponse` plus the rubric used to prompt the LLM, validates every key/label references something real, sums points from the selected tiers, subtracts flag penalties, and derives a recommendation from thresholds. Any validation failure throws `LlmScoringException` — no silent fallback.

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreAssemblerTest {

    private final Rubric rubric = new Rubric(
            UuidFactory.newId(), 1, "default",
            List.of(new Disqualifier("ON_SITE", "on-site required")),
            List.of(
                new Category("compensation", "pay", 20, List.of(
                    new Tier("Excellent", 20, ">$250k"),
                    new Tier("Good", 15, "$200-250k"),
                    new Tier("Fair", 8, "$150-200k"))),
                new Category("remote", "remote friendly", 10, List.of(
                    new Tier("Full remote", 10, "remote allowed"),
                    new Tier("Hybrid", 5, "some days required")))
            ),
            List.of(new Flag("AT_WILL", "aggressive at-will", 3)),
            new Thresholds(25, 20, 10),
            Instant.now());

    private final JobPosting posting = buildPosting();

    private JobPosting buildPosting() {
        var p = new JobPosting();
        p.setId(UuidFactory.newId());
        p.setSource("manual");
        return p;
    }

    private ScoreAssembler assembler() {
        return new ScoreAssembler();
    }

    @Test
    void sumsTierPointsAndSubtractsFlagPenalties() {
        var resp = LlmScoreResponse.of(null,
                List.of(
                    new LlmScoreResponse.CategoryVerdict("compensation", "Good", "salary listed"),
                    new LlmScoreResponse.CategoryVerdict("remote", "Full remote", "remote allowed")),
                List.of(new LlmScoreResponse.FlagFinding("AT_WILL", "at-will clause")));

        ScoreReport report = assembler().assemble(posting, rubric, resp, "claude-sonnet-4-6");

        assertThat(report.rawScore()).isEqualTo(25);
        assertThat(report.finalScore()).isEqualTo(22);
        assertThat(report.recommendation()).isEqualTo(Recommendation.APPLY);
        assertThat(report.disqualifiedBy()).isEmpty();
        assertThat(report.llmModel()).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    void disqualifierForcesSkipAndZeroFinal() {
        var resp = LlmScoreResponse.of("ON_SITE", List.of(), List.of());
        var report = assembler().assemble(posting, rubric, resp, "claude-sonnet-4-6");

        assertThat(report.recommendation()).isEqualTo(Recommendation.SKIP);
        assertThat(report.finalScore()).isEqualTo(0);
        assertThat(report.disqualifiedBy()).isPresent();
        assertThat(report.disqualifiedBy().get().key()).isEqualTo("ON_SITE");
    }

    @Test
    void finalScoreFlooredAtZero() {
        var cheapRubric = new Rubric(UuidFactory.newId(), 1, "tiny",
                List.of(),
                List.of(new Category("c", "x", 5,
                    List.of(new Tier("Low", 2, "low")))),
                List.of(new Flag("BIG", "big", 100)),
                new Thresholds(100, 50, 10), Instant.now());
        var resp = LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("c", "Low", "r")),
                List.of(new LlmScoreResponse.FlagFinding("BIG", "r")));
        var report = assembler().assemble(posting, cheapRubric, resp, "m");
        assertThat(report.finalScore()).isEqualTo(0);
    }

    @Test
    void unknownCategoryKeyThrows() {
        var resp = LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("bogus", "Good", "r")),
                List.of());
        assertThatThrownBy(() -> assembler().assemble(posting, rubric, resp, "m"))
                .isInstanceOf(LlmScoringException.class)
                .hasMessageContaining("bogus");
    }

    @Test
    void unknownTierLabelThrows() {
        var resp = LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Stupendous", "r")),
                List.of());
        assertThatThrownBy(() -> assembler().assemble(posting, rubric, resp, "m"))
                .isInstanceOf(LlmScoringException.class)
                .hasMessageContaining("Stupendous");
    }

    @Test
    void unknownFlagKeyThrows() {
        var resp = LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "r"),
                        new LlmScoreResponse.CategoryVerdict("remote", "Hybrid", "r")),
                List.of(new LlmScoreResponse.FlagFinding("BOGUS", "r")));
        assertThatThrownBy(() -> assembler().assemble(posting, rubric, resp, "m"))
                .isInstanceOf(LlmScoringException.class)
                .hasMessageContaining("BOGUS");
    }

    @Test
    void unknownDisqualifierKeyThrows() {
        var resp = LlmScoreResponse.of("BOGUS", List.of(), List.of());
        assertThatThrownBy(() -> assembler().assemble(posting, rubric, resp, "m"))
                .isInstanceOf(LlmScoringException.class);
    }

    @Test
    void missingCategoryCoverageThrows() {
        var resp = LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "r")),
                List.of());
        assertThatThrownBy(() -> assembler().assemble(posting, rubric, resp, "m"))
                .isInstanceOf(LlmScoringException.class)
                .hasMessageContaining("remote");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=ScoreAssemblerTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `LlmScoringException.java`**

```java
package com.majordomo.application.envoy;

/**
 * Thrown when LLM output fails validation against the active rubric, or when
 * the LLM call itself fails at the transport layer. Unchecked because the
 * controller layer maps it to a 502 Bad Gateway — callers should not attempt
 * recovery at finer granularity.
 */
public class LlmScoringException extends RuntimeException {

    /** Constructs an exception with the given message. */
    public LlmScoringException(String message) {
        super(message);
    }

    /** Constructs an exception with the given message and cause. */
    public LlmScoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Create `ScoreAssembler.java`**

```java
package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.CategoryScore;
import com.majordomo.domain.model.envoy.Disqualifier;
import com.majordomo.domain.model.envoy.Flag;
import com.majordomo.domain.model.envoy.FlagHit;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns an {@link LlmScoreResponse} into a validated, persisted-ready
 * {@link ScoreReport}. All validation is deterministic; any mismatch between the
 * LLM output and the rubric throws {@link LlmScoringException}.
 */
@Component
public class ScoreAssembler {

    /**
     * Validates {@code resp} against {@code rubric} and assembles a {@code ScoreReport}.
     * Does not persist.
     *
     * @param posting  the posting being scored
     * @param rubric   the rubric used to prompt the LLM
     * @param resp     the LLM's structured response
     * @param llmModel model identifier to record on the report
     * @return a fully-validated report ready to persist
     */
    public ScoreReport assemble(JobPosting posting, Rubric rubric,
                                LlmScoreResponse resp, String llmModel) {

        if (resp.disqualifierKey().isPresent()) {
            Disqualifier dq = lookupDisqualifier(rubric, resp.disqualifierKey().get());
            return new ScoreReport(
                    UuidFactory.newId(),
                    posting.getOrganizationId(),
                    posting.getId(),
                    rubric.id(),
                    rubric.version(),
                    Optional.of(dq),
                    List.of(),
                    List.of(),
                    0, 0,
                    Recommendation.SKIP,
                    llmModel,
                    Instant.now());
        }

        requireAllCategoriesCovered(rubric, resp);

        List<CategoryScore> categoryScores = new ArrayList<>();
        int rawScore = 0;
        for (var verdict : resp.categoryVerdicts()) {
            Tier tier = lookupTier(rubric, verdict.categoryKey(), verdict.tierLabel());
            categoryScores.add(new CategoryScore(
                    verdict.categoryKey(), tier.points(), tier.label(), verdict.rationale()));
            rawScore += tier.points();
        }

        List<FlagHit> flagHits = new ArrayList<>();
        int totalPenalty = 0;
        for (var finding : resp.flagHits()) {
            Flag flag = lookupFlag(rubric, finding.flagKey());
            flagHits.add(new FlagHit(flag.key(), flag.penalty(), finding.rationale()));
            totalPenalty += flag.penalty();
        }

        int finalScore = Math.max(0, rawScore - totalPenalty);
        Recommendation recommendation = deriveRecommendation(finalScore, rubric.thresholds());

        return new ScoreReport(
                UuidFactory.newId(),
                posting.getId(),
                rubric.id(),
                rubric.version(),
                Optional.empty(),
                List.copyOf(categoryScores),
                List.copyOf(flagHits),
                rawScore,
                finalScore,
                recommendation,
                llmModel,
                Instant.now());
    }

    private Disqualifier lookupDisqualifier(Rubric rubric, String key) {
        return rubric.disqualifiers().stream()
                .filter(d -> d.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new LlmScoringException(
                        "LLM returned unknown disqualifier key: " + key));
    }

    private Tier lookupTier(Rubric rubric, String categoryKey, String tierLabel) {
        var cat = rubric.categories().stream()
                .filter(c -> c.key().equals(categoryKey))
                .findFirst()
                .orElseThrow(() -> new LlmScoringException(
                        "LLM returned unknown category key: " + categoryKey));
        return cat.tiers().stream()
                .filter(t -> t.label().equals(tierLabel))
                .findFirst()
                .orElseThrow(() -> new LlmScoringException(
                        "LLM returned unknown tier label '" + tierLabel
                                + "' for category '" + categoryKey + "'"));
    }

    private Flag lookupFlag(Rubric rubric, String flagKey) {
        return rubric.flags().stream()
                .filter(f -> f.key().equals(flagKey))
                .findFirst()
                .orElseThrow(() -> new LlmScoringException(
                        "LLM returned unknown flag key: " + flagKey));
    }

    private void requireAllCategoriesCovered(Rubric rubric, LlmScoreResponse resp) {
        var coveredKeys = resp.categoryVerdicts().stream()
                .map(LlmScoreResponse.CategoryVerdict::categoryKey)
                .toList();
        for (var cat : rubric.categories()) {
            if (!coveredKeys.contains(cat.key())) {
                throw new LlmScoringException(
                        "LLM response missing required category: " + cat.key());
            }
        }
    }

    private Recommendation deriveRecommendation(int finalScore, Thresholds t) {
        if (finalScore >= t.applyImmediately()) {
            return Recommendation.APPLY_NOW;
        }
        if (finalScore >= t.apply()) {
            return Recommendation.APPLY;
        }
        if (finalScore >= t.considerOnly()) {
            return Recommendation.CONSIDER;
        }
        return Recommendation.SKIP;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw -Dtest=ScoreAssemblerTest test`
Expected: 8 tests pass.

- [ ] **Step 6: Commit (includes Task 7 outbound ports)**

```bash
git add src/main/java/com/majordomo/domain/port/out/envoy/ \
        src/main/java/com/majordomo/application/envoy/ \
        src/test/java/com/majordomo/application/envoy/ScoreAssemblerTest.java
git commit -m "add envoy ScoreAssembler and outbound ports"
```

---

### Task 9: Build `PromptBuilder` — serialise rubric and posting for the LLM

**Files:**
- Create: `src/main/java/com/majordomo/application/envoy/PromptBuilder.java`
- Test: `src/test/java/com/majordomo/application/envoy/PromptBuilderTest.java`

The prompt is a system message describing the rubric in JSON, plus a user message with the posting. The system message embeds the rubric verbatim so the LLM can reference exact category keys and tier labels. We do NOT include point values in the prompt — the LLM's job is tier selection, not arithmetic.

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    @Test
    void buildsSystemPromptContainingCategoryKeysAndTierLabels() {
        var rubric = new Rubric(UuidFactory.newId(), 1, "default",
                List.of(new Disqualifier("ON_SITE", "on-site required")),
                List.of(new Category("compensation", "pay", 20, List.of(
                        new Tier("Excellent", 20, ">$250k"),
                        new Tier("Good", 15, "$200-250k")))),
                List.of(new Flag("AT_WILL", "aggressive at-will", 3)),
                new Thresholds(25, 20, 10), Instant.now());

        var posting = new JobPosting();
        posting.setCompany("Acme");
        posting.setTitle("Senior Engineer");
        posting.setRawText("We offer...");

        ScoringPrompt p = new PromptBuilder().build(posting, rubric);

        assertThat(p.systemPrompt()).contains("compensation");
        assertThat(p.systemPrompt()).contains("Excellent").contains("Good");
        assertThat(p.systemPrompt()).contains("ON_SITE");
        assertThat(p.systemPrompt()).contains("AT_WILL");
        assertThat(p.systemPrompt()).doesNotContain("\"points\"");
        assertThat(p.userPrompt()).contains("Acme").contains("Senior Engineer").contains("We offer");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=PromptBuilderTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `ScoringPrompt.java`**

`src/main/java/com/majordomo/application/envoy/ScoringPrompt.java`:

```java
package com.majordomo.application.envoy;

/**
 * A pair of prompts built from a rubric and posting. The system prompt is stable
 * per rubric version and a prime prompt-cache candidate when many postings are
 * scored against the same rubric.
 *
 * @param systemPrompt rubric-derived instructions and schema
 * @param userPrompt   the posting, formatted for the LLM
 */
public record ScoringPrompt(String systemPrompt, String userPrompt) { }
```

- [ ] **Step 4: Create `PromptBuilder.java`**

```java
package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.Tier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds a {@link ScoringPrompt} from a rubric and posting. The rubric is
 * rendered without point values so the LLM cannot anchor on numbers when
 * choosing tiers.
 */
@Component
public class PromptBuilder {

    /**
     * Builds the scoring prompt for this posting + rubric pair.
     */
    public ScoringPrompt build(JobPosting posting, Rubric rubric) {
        return new ScoringPrompt(renderSystemPrompt(rubric), renderUserPrompt(posting));
    }

    private String renderSystemPrompt(Rubric rubric) {
        var sb = new StringBuilder();
        sb.append("You are a job-posting scorer. You will read a job posting and ")
          .append("return a strict JSON object matching this schema:\n\n")
          .append("""
                  {
                    "disqualifierKey": string | null,
                    "categoryVerdicts": [
                      {"categoryKey": string, "tierLabel": string, "rationale": string}
                    ],
                    "flagHits": [
                      {"flagKey": string, "rationale": string}
                    ]
                  }
                  """)
          .append("\nReturn exactly one categoryVerdict per category below.\n")
          .append("If any disqualifier applies, set disqualifierKey to its key and ")
          .append("return empty categoryVerdicts and flagHits.\n\n");

        sb.append("## Disqualifiers (hard fails)\n");
        if (rubric.disqualifiers().isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (var d : rubric.disqualifiers()) {
                sb.append("- `").append(d.key()).append("`: ").append(d.description()).append("\n");
            }
        }

        sb.append("\n## Categories\n");
        for (Category c : rubric.categories()) {
            sb.append("\n### `").append(c.key()).append("` — ").append(c.description()).append("\n");
            sb.append("Pick ONE tier by label:\n");
            for (Tier t : c.tiers()) {
                sb.append("- \"").append(t.label()).append("\": ").append(t.criteria()).append("\n");
            }
        }

        sb.append("\n## Flags (soft, cumulative)\n");
        if (rubric.flags().isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (var f : rubric.flags()) {
                sb.append("- `").append(f.key()).append("`: ").append(f.description()).append("\n");
            }
        }

        sb.append("\nRespond with JSON only. No prose, no code fences.");
        return sb.toString();
    }

    private String renderUserPrompt(JobPosting posting) {
        var sb = new StringBuilder();
        sb.append("Company: ").append(nullSafe(posting.getCompany())).append("\n")
          .append("Title: ").append(nullSafe(posting.getTitle())).append("\n")
          .append("Location: ").append(nullSafe(posting.getLocation())).append("\n");
        Map<String, String> extracted = posting.getExtracted();
        if (extracted != null && !extracted.isEmpty()) {
            sb.append("Extracted fields:\n");
            sb.append(extracted.entrySet().stream()
                    .map(e -> "  - " + e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("\n")));
            sb.append("\n");
        }
        sb.append("\n--- POSTING ---\n").append(nullSafe(posting.getRawText()));
        return sb.toString();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -Dtest=PromptBuilderTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/majordomo/application/envoy/ \
        src/test/java/com/majordomo/application/envoy/PromptBuilderTest.java
git commit -m "add envoy PromptBuilder"
```

---

### Task 10: Anthropic SDK scoring adapter

> **Skill cross-reference (required):** Invoke the `claude-api` skill before writing this task — it documents current SDK versions, model IDs, prompt-caching patterns, and the `AnthropicClient` builder API. The snippets below are a starting skeleton; verify against what the skill reports (particularly the exact cache-control and system-prompt block API, which has evolved across SDK versions).

**Files:**
- Modify: `pom.xml` (add `com.anthropic:anthropic-java` dependency)
- Create: `src/main/java/com/majordomo/adapter/out/llm/AnthropicMessageClient.java`
- Create: `src/main/java/com/majordomo/adapter/out/llm/LlmConfiguration.java`
- Create: `src/main/java/com/majordomo/adapter/out/llm/AnthropicLlmScoringAdapter.java`
- Modify: `src/main/resources/application.yml` (add `envoy.llm.*` properties; Resilience4j `envoy-llm` instance)
- Test: `src/test/java/com/majordomo/adapter/out/llm/AnthropicLlmScoringAdapterTest.java`

We use the official Anthropic Java SDK (`com.anthropic:anthropic-java`). The SDK's `AnthropicClient` accepts a custom `baseUrl(...)`, so `MockWebServer` still works for tests. The system prompt is sent as a `TextBlockParam` with `cache_control: ephemeral` so subsequent postings against the same rubric hit the 5-minute Anthropic prompt cache.

- [ ] **Step 1: Add the Anthropic SDK to `pom.xml`**

Add to the main `<dependencies>` block (not test scope):

```xml
<dependency>
    <groupId>com.anthropic</groupId>
    <artifactId>anthropic-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

> Verify the latest stable version against Maven Central before committing. The SDK's public API has shifted between 0.x and 1.x lines — the `claude-api` skill reports current conventions.

Also add `okhttp-mockwebserver` (test scope) if not already present:

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <version>4.12.0</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write the failing test using `MockWebServer`**

The SDK builder exposes `.baseUrl(String)` so we can point it at a `MockWebServer` url.


```java
package com.majordomo.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.application.envoy.PromptBuilder;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicLlmScoringAdapterTest {

    private MockWebServer server;
    private AnthropicLlmScoringAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl(server.url("/").toString())
                .build();
        adapter = new AnthropicLlmScoringAdapter(
                new AnthropicMessageClient(client, "claude-sonnet-4-6", 4096),
                new PromptBuilder());
    }

    @AfterEach
    void tearDown() throws Exception { server.shutdown(); }

    private Rubric rubric() {
        return new Rubric(UuidFactory.newId(), Optional.empty(), 1, "default",
                List.of(),
                List.of(new Category("compensation", "pay", 20,
                        List.of(new Tier("Good", 15, "$200-250k")))),
                List.of(), new Thresholds(20, 15, 5), Instant.now());
    }

    private JobPosting posting() {
        var p = new JobPosting();
        p.setOrganizationId(UuidFactory.newId());
        p.setRawText("We pay well");
        return p;
    }

    @Test
    void parsesValidAnthropicResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "msg_01",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-sonnet-4-6",
                          "stop_reason": "end_turn",
                          "usage": {"input_tokens": 10, "output_tokens": 10},
                          "content": [
                            {"type": "text", "text": "{\\"disqualifierKey\\":null,\\"categoryVerdicts\\":[{\\"categoryKey\\":\\"compensation\\",\\"tierLabel\\":\\"Good\\",\\"rationale\\":\\"listed salary\\"}],\\"flagHits\\":[]}"}
                          ]
                        }
                        """));

        LlmScoreResponse resp = adapter.score(posting(), rubric());

        assertThat(resp.disqualifierKey()).isEmpty();
        assertThat(resp.categoryVerdicts()).hasSize(1);
        assertThat(resp.categoryVerdicts().get(0).tierLabel()).isEqualTo("Good");
    }

    @Test
    void throwsOnNon2xx() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
        assertThatThrownBy(() -> adapter.score(posting(), rubric()))
                .isInstanceOf(LlmScoringException.class);
    }

    @Test
    void throwsOnMalformedJsonInAssistantMessage() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "msg_01", "type": "message", "role": "assistant",
                          "model": "claude-sonnet-4-6", "stop_reason": "end_turn",
                          "usage": {"input_tokens": 1, "output_tokens": 1},
                          "content": [{"type": "text", "text": "not json at all"}]
                        }
                        """));
        assertThatThrownBy(() -> adapter.score(posting(), rubric()))
                .isInstanceOf(LlmScoringException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=AnthropicLlmScoringAdapterTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `AnthropicMessageClient.java`**

Thin wrapper over `AnthropicClient` that applies Resilience4j and normalizes the SDK exceptions into `LlmScoringException`. The exact SDK call signatures below are current as of `anthropic-java 1.0.0` — if the skill reports a different builder shape, follow the skill.

```java
package com.majordomo.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.majordomo.application.envoy.LlmScoringException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.List;

/**
 * Thin wrapper over {@link AnthropicClient}. Applies a Resilience4j circuit breaker
 * and retry policy named {@code envoy-llm}. Callers supply a system prompt (marked
 * cacheable) and a user prompt; returns the assistant's first text block.
 */
public class AnthropicMessageClient {

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    /**
     * @param client    pre-built Anthropic SDK client
     * @param model     Anthropic model id (e.g. "claude-sonnet-4-6")
     * @param maxTokens response length cap
     */
    public AnthropicMessageClient(AnthropicClient client, String model, int maxTokens) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /** Returns the model id for reporting. */
    public String model() { return model; }

    /**
     * Sends one Messages API call and returns the assistant's first text block.
     * The system prompt is attached with {@code cache_control: ephemeral} so
     * rubric-sized prompts stay in the 5-minute Anthropic prompt cache across
     * scoring calls.
     */
    @CircuitBreaker(name = "envoy-llm")
    @Retry(name = "envoy-llm")
    public String send(String systemPrompt, String userPrompt) {
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .systemOfTextBlockParams(List.of(
                            TextBlockParam.builder()
                                    .text(systemPrompt)
                                    .cacheControl(CacheControlEphemeral.builder().build())
                                    .build()))
                    .addUserMessage(userPrompt)
                    .build();
            Message message = client.messages().create(params);
            List<ContentBlock> blocks = message.content();
            if (blocks == null || blocks.isEmpty()) {
                throw new LlmScoringException("Anthropic API returned no content");
            }
            return blocks.get(0).text()
                    .map(tb -> tb.text())
                    .orElseThrow(() -> new LlmScoringException(
                            "First content block was not text"));
        } catch (LlmScoringException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmScoringException("Anthropic API call failed", e);
        }
    }
}
```

- [ ] **Step 4: Create `AnthropicLlmScoringAdapter.java`**

```java
package com.majordomo.adapter.out.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.application.envoy.PromptBuilder;
import com.majordomo.application.envoy.ScoringPrompt;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import org.springframework.stereotype.Component;

/**
 * Scores postings via the Anthropic SDK with a rubric-derived system prompt and
 * the posting as the user message. Delegates prompt rendering to
 * {@link PromptBuilder} and SDK plumbing to {@link AnthropicMessageClient}.
 */
@Component
public class AnthropicLlmScoringAdapter implements LlmScoringPort {

    private final AnthropicMessageClient client;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicLlmScoringAdapter(AnthropicMessageClient client, PromptBuilder promptBuilder) {
        this.client = client;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public LlmScoreResponse score(JobPosting posting, Rubric rubric) {
        ScoringPrompt prompt = promptBuilder.build(posting, rubric);
        String json = client.send(prompt.systemPrompt(), prompt.userPrompt());
        try {
            return mapper.readValue(json, LlmScoreResponse.class);
        } catch (Exception e) {
            throw new LlmScoringException("LLM returned unparseable JSON: " + json, e);
        }
    }

    @Override
    public String modelId() { return client.model(); }
}
```

- [ ] **Step 5: Wire the SDK client bean and config properties**

Create `src/main/java/com/majordomo/adapter/out/llm/LlmConfiguration.java`:

```java
package com.majordomo.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Anthropic SDK transport. Reads API key, model,
 * and token limit from {@code envoy.llm.*} properties.
 */
@Configuration
public class LlmConfiguration {

    /**
     * Constructs the shared Anthropic SDK client.
     *
     * @param apiKey    Anthropic API key ({@code envoy.llm.api-key})
     * @param baseUrl   optional API base URL override ({@code envoy.llm.base-url})
     */
    @Bean
    public AnthropicClient anthropicClient(
            @Value("${envoy.llm.api-key}") String apiKey,
            @Value("${envoy.llm.base-url:}") String baseUrl) {
        var builder = AnthropicOkHttpClient.builder().apiKey(apiKey);
        if (!baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    /** Wraps the SDK client with model and token defaults for envoy. */
    @Bean
    public AnthropicMessageClient anthropicMessageClient(
            AnthropicClient client,
            @Value("${envoy.llm.model:claude-sonnet-4-6}") String model,
            @Value("${envoy.llm.max-tokens:4096}") int maxTokens) {
        return new AnthropicMessageClient(client, model, maxTokens);
    }
}
```

- [ ] **Step 6: Add config to `application.yml`**

Open `src/main/resources/application.yml`, add at the top level (after existing `spring:` block):

```yaml
envoy:
  llm:
    api-key: ${ANTHROPIC_API_KEY:}
    base-url: https://api.anthropic.com
    model: claude-sonnet-4-6
    max-tokens: 4096

resilience4j:
  circuitbreaker:
    instances:
      envoy-llm:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
  retry:
    instances:
      envoy-llm:
        max-attempts: 3
        wait-duration: 2s
        retry-exceptions:
          - com.majordomo.application.envoy.LlmScoringException
```

> If `resilience4j:` already exists at the top level, merge the `envoy-llm` entries in rather than duplicating the parent key.

- [ ] **Step 7: Run tests to verify they pass**

Run: `./mvnw -Dtest=AnthropicLlmScoringAdapterTest test`
Expected: 3 tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/majordomo/adapter/out/llm/ \
        src/test/java/com/majordomo/adapter/out/llm/ \
        src/main/resources/application.yml \
        pom.xml
git commit -m "add envoy AnthropicLlmScoringAdapter with resilience4j"
```

---

### Task 11: Inbound ports and the `JobScorer` application service

**Files:**
- Create: `src/main/java/com/majordomo/domain/port/in/envoy/ScoreJobPostingUseCase.java`
- Create: `src/main/java/com/majordomo/domain/port/in/envoy/IngestJobPostingUseCase.java`
- Create: `src/main/java/com/majordomo/application/envoy/JobScorer.java`
- Test: `src/test/java/com/majordomo/application/envoy/JobScorerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.*;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobScorerTest {

    @Mock RubricRepository rubrics;
    @Mock JobPostingRepository postings;
    @Mock ScoreReportRepository reports;
    @Mock LlmScoringPort llm;

    private JobScorer scorer;

    private Rubric rubric;
    private JobPosting posting;

    @BeforeEach
    void setUp() {
        scorer = new JobScorer(rubrics, postings, reports, llm,
                new ScoreAssembler(), new PromptBuilder());
        rubric = new Rubric(UuidFactory.newId(), 1, "default",
                List.of(),
                List.of(new Category("compensation", "pay", 20,
                        List.of(new Tier("Good", 15, "$200-250k")))),
                List.of(), new Thresholds(20, 15, 5), Instant.now());
        posting = new JobPosting();
        posting.setId(UuidFactory.newId());
        posting.setRawText("We pay $220k");
    }

    @Test
    void scorePersistsReportAndReturnsIt() {
        when(postings.findById(posting.getId())).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("default")).thenReturn(Optional.of(rubric));
        when(llm.score(any(), any())).thenReturn(LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "listed")),
                List.of()));
        when(llm.modelId()).thenReturn("claude-sonnet-4-6");
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScoreReport r = scorer.score(posting.getId(), "default");

        assertThat(r.finalScore()).isEqualTo(15);
        assertThat(r.recommendation()).isEqualTo(Recommendation.APPLY);
        assertThat(r.llmModel()).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    void throwsWhenPostingMissing() {
        UUID bogus = UuidFactory.newId();
        when(postings.findById(bogus)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> scorer.score(bogus, "default"))
                .isInstanceOf(com.majordomo.domain.model.EntityNotFoundException.class);
    }

    @Test
    void throwsWhenRubricMissing() {
        when(postings.findById(posting.getId())).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("default")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> scorer.score(posting.getId(), "default"))
                .isInstanceOf(com.majordomo.domain.model.EntityNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=JobScorerTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create the inbound ports**

`ScoreJobPostingUseCase.java`:

```java
package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.ScoreReport;

import java.util.UUID;

/** Inbound port for scoring a previously-ingested posting against an active rubric. */
public interface ScoreJobPostingUseCase {

    /**
     * Scores a posting using the active rubric for {@code (organizationId, rubricName)}.
     *
     * @param postingId      the posting to score
     * @param rubricName     rubric name (e.g. "default")
     * @param organizationId the requesting org (must own the posting)
     * @return the persisted score report
     */
    ScoreReport score(UUID postingId, String rubricName, UUID organizationId);
}
```

`IngestJobPostingUseCase.java`:

```java
package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;

/** Inbound port for ingesting a new posting from any {@code JobSource}. */
public interface IngestJobPostingUseCase {

    /**
     * Ingests a posting into the given organization. Routes {@code request} to the
     * first {@code JobSource} whose {@code supports(...)} returns true. Persists
     * and returns the posting with {@code organizationId} set.
     */
    JobPosting ingest(JobSourceRequest request, UUID organizationId);
}
```

- [ ] **Step 4: Create `JobScorer.java`**

```java
package com.majordomo.application.envoy;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates posting scoring. Java owns all deterministic work: load the
 * posting and active rubric, render the prompt, validate the LLM response via
 * {@link ScoreAssembler}, and persist the report. The LLM makes only the fuzzy
 * interpretive choices.
 */
@Service
public class JobScorer implements ScoreJobPostingUseCase {

    private final RubricRepository rubrics;
    private final JobPostingRepository postings;
    private final ScoreReportRepository reports;
    private final LlmScoringPort llm;
    private final ScoreAssembler assembler;
    private final PromptBuilder promptBuilder;

    /** Constructs the scorer with all required collaborators. */
    public JobScorer(RubricRepository rubrics,
                     JobPostingRepository postings,
                     ScoreReportRepository reports,
                     LlmScoringPort llm,
                     ScoreAssembler assembler,
                     PromptBuilder promptBuilder) {
        this.rubrics = rubrics;
        this.postings = postings;
        this.reports = reports;
        this.llm = llm;
        this.assembler = assembler;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public ScoreReport score(UUID postingId, String rubricName, UUID organizationId) {
        JobPosting posting = postings.findById(postingId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        EntityType.JOB_POSTING.name(), postingId));
        Rubric rubric = rubrics.findActiveByName(rubricName, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("RUBRIC", rubricName));

        LlmScoreResponse resp = llm.score(posting, rubric);
        ScoreReport report = assembler.assemble(posting, rubric, resp, llm.modelId());
        return reports.save(report);
    }
}
```

- [ ] **Step 5: Add `JOB_POSTING` to the `EntityType` enum**

Open `src/main/java/com/majordomo/domain/model/EntityType.java` and add `JOB_POSTING` to the existing enum (alphabetical order if present). If the file uses string constants, add `JOB_POSTING` in the same style.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./mvnw -Dtest=JobScorerTest test`
Expected: 3 tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/majordomo/domain/port/in/envoy/ \
        src/main/java/com/majordomo/application/envoy/JobScorer.java \
        src/main/java/com/majordomo/domain/model/EntityType.java \
        src/test/java/com/majordomo/application/envoy/JobScorerTest.java
git commit -m "add envoy JobScorer and inbound ports"
```

---

### Task 12: `JobSource` interface, `ManualPasteSource`, and `JobIngestionService`

**Files:**
- Create: `src/main/java/com/majordomo/adapter/out/ingest/JobSource.java`
- Create: `src/main/java/com/majordomo/adapter/out/ingest/ManualPasteSource.java`
- Create: `src/main/java/com/majordomo/application/envoy/JobIngestionService.java`
- Test: `src/test/java/com/majordomo/application/envoy/JobIngestionServiceTest.java`
- Test: `src/test/java/com/majordomo/adapter/out/ingest/ManualPasteSourceTest.java`

- [ ] **Step 1: Write the failing test for `ManualPasteSource`**

```java
package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManualPasteSourceTest {

    private final ManualPasteSource source = new ManualPasteSource();

    @Test
    void supportsManualType() {
        assertThat(source.supports(new JobSourceRequest("manual", "x", Map.of()))).isTrue();
        assertThat(source.supports(new JobSourceRequest("url", "x", Map.of()))).isFalse();
    }

    @Test
    void fetchReturnsPostingWithRawTextAndHints() {
        var req = new JobSourceRequest("manual", "we are hiring...",
                Map.of("company", "Acme", "title", "Senior Engineer"));
        JobPosting p = source.fetch(req);

        assertThat(p.getRawText()).isEqualTo("we are hiring...");
        assertThat(p.getCompany()).isEqualTo("Acme");
        assertThat(p.getTitle()).isEqualTo("Senior Engineer");
        assertThat(p.getSource()).isEqualTo("manual");
        assertThat(p.getFetchedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=ManualPasteSourceTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `JobSource.java`**

```java
package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;

/**
 * A pluggable ingestion source. Implementations are Spring-discovered and routed
 * by {@code JobIngestionService} — no central registry.
 */
public interface JobSource {

    /** Stable name ({@code "manual"}, {@code "url"}, {@code "greenhouse"}, ...). */
    String name();

    /** True iff this source can handle the given request. */
    boolean supports(JobSourceRequest request);

    /**
     * Fetches the posting for {@code request}. Returns a posting with its
     * {@code source}, {@code rawText}, and {@code fetchedAt} populated at minimum.
     */
    JobPosting fetch(JobSourceRequest request);
}
```

- [ ] **Step 4: Create `ManualPasteSource.java`**

```java
package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Ingests raw pasted posting text. {@code request.payload()} holds the body;
 * {@code request.hints()} optionally supplies {@code company}, {@code title},
 * {@code location}, and {@code externalId}.
 */
@Component
public class ManualPasteSource implements JobSource {

    @Override
    public String name() { return "manual"; }

    @Override
    public boolean supports(JobSourceRequest request) {
        return "manual".equals(request.type());
    }

    @Override
    public JobPosting fetch(JobSourceRequest request) {
        var p = new JobPosting();
        p.setSource("manual");
        p.setRawText(request.payload());
        p.setFetchedAt(Instant.now());
        Map<String, String> hints = request.hints() == null ? Map.of() : request.hints();
        p.setCompany(hints.get("company"));
        p.setTitle(hints.get("title"));
        p.setLocation(hints.get("location"));
        p.setExternalId(hints.get("externalId"));
        p.setExtracted(new HashMap<>(hints));
        return p;
    }
}
```

- [ ] **Step 5: Write the failing test for `JobIngestionService`**

```java
package com.majordomo.application.envoy;

import com.majordomo.adapter.out.ingest.JobSource;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobIngestionServiceTest {

    @Test
    void routesToFirstSupportingSourceAndPersists() {
        var matching = mock(JobSource.class);
        when(matching.supports(any())).thenReturn(true);
        when(matching.fetch(any())).thenAnswer(inv -> {
            var p = new JobPosting();
            p.setSource("manual");
            p.setRawText("body");
            return p;
        });
        var nonMatching = mock(JobSource.class);
        when(nonMatching.supports(any())).thenReturn(false);

        var repo = mock(JobPostingRepository.class);
        when(repo.findBySourceAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> {
            JobPosting p = inv.getArgument(0);
            p.setId(UuidFactory.newId());
            return p;
        });

        var service = new JobIngestionService(List.of(nonMatching, matching), repo);
        JobPosting saved = service.ingest(new JobSourceRequest("manual", "body", Map.of()));

        assertThat(saved.getId()).isNotNull();
        verify(matching).fetch(any());
        verify(nonMatching, never()).fetch(any());
    }

    @Test
    void throwsWhenNoSourceSupportsTheRequest() {
        var source = mock(JobSource.class);
        when(source.supports(any())).thenReturn(false);
        var service = new JobIngestionService(List.of(source), mock(JobPostingRepository.class));

        assertThatThrownBy(() -> service.ingest(new JobSourceRequest("mystery", "", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reusesExistingPostingWhenSourceAndExternalIdMatch() {
        var existing = new JobPosting();
        existing.setId(UuidFactory.newId());
        existing.setSource("greenhouse");
        existing.setExternalId("abc");

        var source = mock(JobSource.class);
        when(source.supports(any())).thenReturn(true);
        when(source.fetch(any())).thenAnswer(inv -> {
            var p = new JobPosting();
            p.setSource("greenhouse");
            p.setExternalId("abc");
            return p;
        });

        var repo = mock(JobPostingRepository.class);
        when(repo.findBySourceAndExternalId("greenhouse", "abc")).thenReturn(Optional.of(existing));

        var service = new JobIngestionService(List.of(source), repo);
        JobPosting result = service.ingest(new JobSourceRequest("greenhouse", "abc", Map.of()));

        assertThat(result.getId()).isEqualTo(existing.getId());
        verify(repo, never()).save(any());
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./mvnw -Dtest=JobIngestionServiceTest,ManualPasteSourceTest test`
Expected: COMPILATION FAILURE for `JobIngestionService`.

- [ ] **Step 7: Create `JobIngestionService.java`**

```java
package com.majordomo.application.envoy;

import com.majordomo.adapter.out.ingest.JobSource;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Routes ingestion requests to the first {@link JobSource} whose
 * {@link JobSource#supports(JobSourceRequest)} returns true. Deduplicates by
 * ({@code source}, {@code externalId}); if a posting with the same pair already
 * exists, returns it instead of re-inserting.
 */
@Service
public class JobIngestionService implements IngestJobPostingUseCase {

    private final List<JobSource> sources;
    private final JobPostingRepository postings;

    /** Spring injects all {@link JobSource} beans into {@code sources}. */
    public JobIngestionService(List<JobSource> sources, JobPostingRepository postings) {
        this.sources = sources;
        this.postings = postings;
    }

    @Override
    public JobPosting ingest(JobSourceRequest request, UUID organizationId) {
        JobSource source = sources.stream()
                .filter(s -> s.supports(request))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No JobSource supports request type: " + request.type()));

        JobPosting fetched = source.fetch(request);
        fetched.setOrganizationId(organizationId);
        fetched.setFetchedAt(Instant.now());

        if (fetched.getExternalId() != null) {
            var existing = postings.findBySourceAndExternalId(
                    fetched.getSource(), fetched.getExternalId(), organizationId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (fetched.getId() == null) {
            fetched.setId(UuidFactory.newId());
        }
        return postings.save(fetched);
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./mvnw -Dtest=JobIngestionServiceTest,ManualPasteSourceTest test`
Expected: all tests pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/majordomo/adapter/out/ingest/ \
        src/main/java/com/majordomo/application/envoy/JobIngestionService.java \
        src/test/java/com/majordomo/adapter/out/ingest/ \
        src/test/java/com/majordomo/application/envoy/JobIngestionServiceTest.java
git commit -m "add envoy JobSource interface, ManualPasteSource, JobIngestionService"
```

---

### Task 13: In-memory repositories for the Phase-1 vertical slice

**Files:**
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/InMemoryRubricRepository.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/InMemoryJobPostingRepository.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/InMemoryScoreReportRepository.java`

These are **temporary** — they're deleted in Phase 2 when JPA adapters arrive. They exist behind a Spring profile `envoy-memory` so Phase 1 can run without schema changes.

- [ ] **Step 1: Create `InMemoryRubricRepository.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.*;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link RubricRepository} used only in the Phase-1 vertical slice.
 * Seeds a hard-coded "default" rubric on construction so the CLI runner works
 * without database setup. Deleted in Phase 2.
 */
@Repository
@Profile("envoy-memory")
public class InMemoryRubricRepository implements RubricRepository {

    private final Map<UUID, Rubric> byId = new ConcurrentHashMap<>();

    /** Seeds the "default" rubric. */
    public InMemoryRubricRepository() {
        var seed = new Rubric(
                UuidFactory.newId(), 1, "default",
                List.of(
                    new Disqualifier("ON_SITE_ONLY", "Role requires on-site work only"),
                    new Disqualifier("NON_ENGINEERING", "Posting is not an engineering role")),
                List.of(
                    new Category("compensation", "Base salary and equity", 25, List.of(
                        new Tier("Excellent", 25, "Base >$250k or total comp >$400k clearly stated"),
                        new Tier("Good", 18, "Base $200-250k or range suggesting it"),
                        new Tier("Fair", 10, "Base $150-200k"),
                        new Tier("Poor", 3, "Below $150k or no range given"))),
                    new Category("remote", "Remote flexibility", 15, List.of(
                        new Tier("Full remote", 15, "Fully remote, any US location"),
                        new Tier("Hybrid", 7, "Some in-office days required"),
                        new Tier("Regional remote", 3, "Remote but only from specific metros"))),
                    new Category("role_scope", "Seniority and scope match", 20, List.of(
                        new Tier("Strong match", 20, "Staff/Principal level, backend or platform"),
                        new Tier("Aligned", 12, "Senior engineer, clear backend work"),
                        new Tier("Weak", 4, "Junior or unclear scope"))),
                    new Category("team_signals", "Team/manager/culture signals", 15, List.of(
                        new Tier("Strong", 15, "Specific team, named manager, clear mandate"),
                        new Tier("Generic", 6, "Typical posting language"),
                        new Tier("Red", 1, "Mentions hustle culture, grind, 'rockstar' etc."))),
                    new Category("company_stage", "Company stability & stage", 15, List.of(
                        new Tier("Mature", 15, "Public or late-stage with revenue"),
                        new Tier("Growth", 9, "Series B/C/D with traction"),
                        new Tier("Early", 3, "Seed/Series A"),
                        new Tier("Risky", 0, "Pre-seed, no traction signals"))),
                    new Category("tech_stack", "Tech stack alignment", 10, List.of(
                        new Tier("Perfect", 10, "Java/Spring, Postgres, cloud"),
                        new Tier("Adjacent", 5, "Kotlin/Go/Python, same architecture space"),
                        new Tier("Misaligned", 1, "Frontend-heavy or ecosystem mismatch")))
                ),
                List.of(
                    new Flag("AT_WILL_EMPHASIS", "Unusually aggressive at-will language", 3),
                    new Flag("UNPAID_TEST", "Requires unpaid take-home >4 hours", 5),
                    new Flag("VAGUE_COMP", "No compensation range given at all", 2),
                    new Flag("ON_CALL_HEAVY", "Heavy on-call rotation explicitly required", 2)),
                new Thresholds(75, 55, 35),
                Instant.now());
        byId.put(seed.id(), seed);
    }

    @Override
    public Optional<Rubric> findActiveByName(String name) {
        return byId.values().stream()
                .filter(r -> r.name().equals(name))
                .max(Comparator.comparingInt(Rubric::version));
    }

    @Override
    public Optional<Rubric> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<Rubric> findAllVersionsByName(String name) {
        return byId.values().stream()
                .filter(r -> r.name().equals(name))
                .sorted(Comparator.comparingInt(Rubric::version))
                .toList();
    }

    @Override
    public Rubric save(Rubric rubric) {
        byId.put(rubric.id(), rubric);
        return rubric;
    }
}
```

- [ ] **Step 2: Create `InMemoryJobPostingRepository.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link JobPostingRepository} for the Phase-1 vertical slice.
 * Deleted in Phase 2.
 */
@Repository
@Profile("envoy-memory")
public class InMemoryJobPostingRepository implements JobPostingRepository {

    private final Map<UUID, JobPosting> byId = new ConcurrentHashMap<>();

    @Override
    public JobPosting save(JobPosting posting) {
        byId.put(posting.getId(), posting);
        return posting;
    }

    @Override
    public Optional<JobPosting> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<JobPosting> findBySourceAndExternalId(String source, String externalId) {
        return byId.values().stream()
                .filter(p -> Objects.equals(p.getSource(), source)
                        && Objects.equals(p.getExternalId(), externalId))
                .findFirst();
    }
}
```

- [ ] **Step 3: Create `InMemoryScoreReportRepository.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ScoreReportRepository} for the Phase-1 vertical slice. */
@Repository
@Profile("envoy-memory")
public class InMemoryScoreReportRepository implements ScoreReportRepository {

    private final Map<UUID, ScoreReport> byId = new ConcurrentHashMap<>();

    @Override
    public ScoreReport save(ScoreReport report) {
        byId.put(report.id(), report);
        return report;
    }

    @Override
    public Optional<ScoreReport> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Page<ScoreReport> query(Integer minFinalScore, Recommendation recommendation,
                                   UUID cursor, int limit) {
        int clamped = Math.max(1, Math.min(limit, 100));
        var items = byId.values().stream()
                .filter(r -> minFinalScore == null || r.finalScore() >= minFinalScore)
                .filter(r -> recommendation == null || r.recommendation() == recommendation)
                .sorted(Comparator.comparing(ScoreReport::id))
                .dropWhile(r -> cursor != null && r.id().compareTo(cursor) <= 0)
                .limit(clamped + 1L)
                .toList();
        return Page.fromOverfetch(items, limit, ScoreReport::id);
    }
}
```

- [ ] **Step 4: Verify compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/majordomo/adapter/out/persistence/envoy/
git commit -m "add envoy in-memory repositories behind envoy-memory profile"
```

---

### Task 14: End-to-end integration test with a mock Anthropic server

**Files:**
- Test: `src/test/java/com/majordomo/application/envoy/EnvoyVerticalSliceTest.java`

Runs the full stack with the `envoy-memory` profile, `MockWebServer` stubbing Anthropic, and asserts a posting flows from ingestion through scoring to persistence.

- [ ] **Step 1: Write the test**

```java
package com.majordomo.application.envoy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.majordomo.adapter.out.llm.AnthropicMessageClient;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"envoy-memory", "test"})
class EnvoyVerticalSliceTest {

    private static MockWebServer server;
    private static final UUID ORG_ID = UuidFactory.newId();

    @TestConfiguration
    static class Config {
        @Bean @Primary
        AnthropicMessageClient testMessageClient() throws Exception {
            server = new MockWebServer();
            server.start();
            AnthropicClient client = AnthropicOkHttpClient.builder()
                    .apiKey("test-key")
                    .baseUrl(server.url("/").toString())
                    .build();
            return new AnthropicMessageClient(client, "claude-sonnet-4-6", 4096);
        }
    }

    @Autowired IngestJobPostingUseCase ingest;
    @Autowired ScoreJobPostingUseCase score;

    @BeforeEach
    void enqueueAnthropicResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "msg_01", "type": "message", "role": "assistant",
                          "model": "claude-sonnet-4-6", "stop_reason": "end_turn",
                          "usage": {"input_tokens": 10, "output_tokens": 10},
                          "content": [
                            {"type": "text", "text": "{\\"disqualifierKey\\":null,\\"categoryVerdicts\\":[{\\"categoryKey\\":\\"compensation\\",\\"tierLabel\\":\\"Good\\",\\"rationale\\":\\"$225k base listed\\"},{\\"categoryKey\\":\\"remote\\",\\"tierLabel\\":\\"Full remote\\",\\"rationale\\":\\"fully remote US\\"},{\\"categoryKey\\":\\"role_scope\\",\\"tierLabel\\":\\"Aligned\\",\\"rationale\\":\\"Senior backend\\"},{\\"categoryKey\\":\\"team_signals\\",\\"tierLabel\\":\\"Generic\\",\\"rationale\\":\\"typical language\\"},{\\"categoryKey\\":\\"company_stage\\",\\"tierLabel\\":\\"Growth\\",\\"rationale\\":\\"Series C\\"},{\\"categoryKey\\":\\"tech_stack\\",\\"tierLabel\\":\\"Perfect\\",\\"rationale\\":\\"Java/Spring/Postgres\\"}],\\"flagHits\\":[]}"}
                          ]
                        }
                        """));
    }

    @AfterEach
    void shutdown() throws Exception { server.shutdown(); }

    @Test
    void manualPaste_scores_producesReport() {
        JobPosting posting = ingest.ingest(new JobSourceRequest(
                "manual",
                "Senior Backend Engineer at Acme — Java/Spring/Postgres, fully remote US, Series C, $225k base.",
                Map.of("company", "Acme", "title", "Senior Engineer")),
                ORG_ID);

        ScoreReport report = score.score(posting.getId(), "default", ORG_ID);

        assertThat(report.recommendation()).isIn(
                Recommendation.APPLY_NOW, Recommendation.APPLY, Recommendation.CONSIDER);
        assertThat(report.finalScore()).isGreaterThan(0);
        assertThat(report.categoryScores()).hasSize(6);
        assertThat(report.organizationId()).isEqualTo(ORG_ID);
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw -Dtest=EnvoyVerticalSliceTest test`
Expected: PASS.

- [ ] **Step 3: Run full build**

Run: `./mvnw verify`
Expected: BUILD SUCCESS (Checkstyle + ArchUnit + all tests).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/majordomo/application/envoy/EnvoyVerticalSliceTest.java
git commit -m "add envoy vertical-slice integration test"
```

---

> **Phase 1 complete.** Envoy can ingest a manually-pasted posting, call the Anthropic API (stubbed or real), validate the response against the hard-coded default rubric, and produce a `ScoreReport` — all behind the `envoy-memory` profile.

---

## Phase 2 — Persistence

**Goal:** Replace the in-memory repositories with real JPA-backed adapters against PostgreSQL. Rubrics are versioned; the default rubric is seeded from JSON by a Flyway migration.

### Task 15: Flyway schema migration for envoy tables

**Files:**
- Create: `src/main/resources/db/migration/V14__envoy_schema.sql`

- [ ] **Step 1: Confirm highest current migration version**

```bash
ls src/main/resources/db/migration/ | sort -V | tail -5
```
Expected: `V13__add_gallery_fields.sql` is the highest. If another Vn has appeared (someone else merged), pick the next free number and update this task's file name.

- [ ] **Step 2: Write the migration**

```sql
-- envoy: job scoring schema. Multi-tenant by organization_id.
-- rubric.organization_id is nullable: NULL = system default template visible
-- to every org; non-null = org-specific version (shadows system default on
-- findActiveByName).
CREATE TABLE envoy_rubric (
    id UUID PRIMARY KEY,
    organization_id UUID,
    name VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL,
    body JSONB NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100)
);

-- Partial unique indexes: separate uniqueness for system defaults and per-org rubrics.
CREATE UNIQUE INDEX envoy_rubric_system_name_version_idx
    ON envoy_rubric (name, version) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX envoy_rubric_org_name_version_idx
    ON envoy_rubric (organization_id, name, version) WHERE organization_id IS NOT NULL;
CREATE INDEX envoy_rubric_name_idx ON envoy_rubric (name);
CREATE INDEX envoy_rubric_org_idx ON envoy_rubric (organization_id);

CREATE TABLE envoy_job_posting (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    source VARCHAR(50) NOT NULL,
    external_id VARCHAR(200),
    company VARCHAR(200),
    title VARCHAR(300),
    location VARCHAR(200),
    raw_text TEXT NOT NULL,
    extracted JSONB,
    fetched_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    CONSTRAINT envoy_job_posting_org_source_external_unique
        UNIQUE (organization_id, source, external_id)
);

CREATE INDEX envoy_job_posting_org_idx ON envoy_job_posting (organization_id);
CREATE INDEX envoy_job_posting_company_idx ON envoy_job_posting (company);

CREATE TABLE envoy_score_report (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    posting_id UUID NOT NULL REFERENCES envoy_job_posting(id),
    rubric_id UUID NOT NULL REFERENCES envoy_rubric(id),
    rubric_version INTEGER NOT NULL,
    body JSONB NOT NULL,
    final_score INTEGER NOT NULL,
    recommendation VARCHAR(20) NOT NULL,
    scored_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX envoy_score_report_org_idx ON envoy_score_report (organization_id);
CREATE INDEX envoy_score_report_posting_idx ON envoy_score_report (posting_id);
CREATE INDEX envoy_score_report_org_final_score_idx
    ON envoy_score_report (organization_id, final_score DESC);
CREATE INDEX envoy_score_report_org_recommendation_idx
    ON envoy_score_report (organization_id, recommendation);
```

- [ ] **Step 3: Run the migration via a smoke test**

Run: `./mvnw -Dtest=FlywayMigrationIntegrationTest test`
Expected: PASS — Flyway applies V14 against the Testcontainers PostgreSQL.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V14__envoy_schema.sql
git commit -m "add envoy Flyway schema migration"
```

---

### Task 16: `Rubric` JPA entity, mapper, repository, adapter

**Files:**
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/RubricEntity.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/JpaRubricRepository.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/RubricMapper.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/RubricRepositoryAdapter.java`
- Test: `src/test/java/com/majordomo/adapter/out/persistence/envoy/RubricRepositoryAdapterTest.java`

Strategy: store the serialized `Rubric` record as a JSONB `body` column. The scalar columns (`name`, `version`, `effective_from`) exist only for indexed lookup; the source of truth is the JSON.

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.IntegrationTest;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class RubricRepositoryAdapterTest {

    @Autowired RubricRepositoryAdapter repo;

    @Test
    void savesAndFindsActiveByNameReturnsHighestVersion() {
        var v1 = new Rubric(UuidFactory.newId(), 1, "envoy-test",
                List.of(), List.of(cat()), List.of(),
                new Thresholds(20, 15, 5), Instant.now());
        var v2 = new Rubric(UuidFactory.newId(), 2, "envoy-test",
                List.of(), List.of(cat()), List.of(),
                new Thresholds(22, 17, 7), Instant.now());
        repo.save(v1);
        repo.save(v2);

        var active = repo.findActiveByName("envoy-test").orElseThrow();
        assertThat(active.version()).isEqualTo(2);
        assertThat(active.thresholds().applyImmediately()).isEqualTo(22);
    }

    @Test
    void findAllVersionsByName_returnsAscending() {
        var v1 = new Rubric(UuidFactory.newId(), 1, "envoy-hist",
                List.of(), List.of(cat()), List.of(),
                new Thresholds(20, 15, 5), Instant.now());
        var v2 = new Rubric(UuidFactory.newId(), 2, "envoy-hist",
                List.of(), List.of(cat()), List.of(),
                new Thresholds(22, 17, 7), Instant.now());
        repo.save(v2);
        repo.save(v1);

        var all = repo.findAllVersionsByName("envoy-hist");
        assertThat(all).extracting(Rubric::version).containsExactly(1, 2);
    }

    private Category cat() {
        return new Category("c", "x", 10, List.of(new Tier("Only", 5, "x")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=RubricRepositoryAdapterTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `RubricEntity.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "envoy_rubric")
public class RubricEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String body;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by")
    private String createdBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
```

- [ ] **Step 4: Create `JpaRubricRepository.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link RubricEntity}. */
public interface JpaRubricRepository extends JpaRepository<RubricEntity, UUID> {

    /** Highest-version row with this name, if any. */
    Optional<RubricEntity> findFirstByNameOrderByVersionDesc(String name);

    /** All rows for a name, ordered by version ascending. */
    List<RubricEntity> findAllByNameOrderByVersionAsc(String name);
}
```

- [ ] **Step 5: Create `RubricMapper.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.domain.model.envoy.Rubric;

/**
 * Maps between the {@link Rubric} record and {@link RubricEntity}, serialising
 * the record to/from JSON for the {@code body} JSONB column.
 */
final class RubricMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RubricMapper() { }

    static RubricEntity toEntity(Rubric rubric) {
        var e = new RubricEntity();
        e.setId(rubric.id());
        e.setName(rubric.name());
        e.setVersion(rubric.version());
        e.setEffectiveFrom(rubric.effectiveFrom());
        try {
            e.setBody(MAPPER.writeValueAsString(rubric));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialise Rubric", ex);
        }
        return e;
    }

    static Rubric toDomain(RubricEntity e) {
        try {
            return MAPPER.readValue(e.getBody(), Rubric.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialise Rubric " + e.getId(), ex);
        }
    }
}
```

- [ ] **Step 6: Create `RubricRepositoryAdapter.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence adapter fulfilling {@link RubricRepository} via JPA. */
@Repository
@Profile("!envoy-memory")
public class RubricRepositoryAdapter implements RubricRepository {

    private final JpaRubricRepository jpa;

    public RubricRepositoryAdapter(JpaRubricRepository jpa) { this.jpa = jpa; }

    @Override
    public Optional<Rubric> findActiveByName(String name) {
        return jpa.findFirstByNameOrderByVersionDesc(name).map(RubricMapper::toDomain);
    }

    @Override
    public Optional<Rubric> findById(UUID id) {
        return jpa.findById(id).map(RubricMapper::toDomain);
    }

    @Override
    public List<Rubric> findAllVersionsByName(String name) {
        return jpa.findAllByNameOrderByVersionAsc(name).stream()
                .map(RubricMapper::toDomain)
                .toList();
    }

    @Override
    public Rubric save(Rubric rubric) {
        return RubricMapper.toDomain(jpa.save(RubricMapper.toEntity(rubric)));
    }
}
```

> Also update `InMemoryRubricRepository` in Task 13 to use `@Profile("envoy-memory")` only (already done above). The JPA adapter uses `!envoy-memory` so only one is active.

- [ ] **Step 7: Run tests to verify they pass**

Run: `./mvnw -Dtest=RubricRepositoryAdapterTest test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/majordomo/adapter/out/persistence/envoy/Rubric* \
        src/main/java/com/majordomo/adapter/out/persistence/envoy/JpaRubricRepository.java \
        src/test/java/com/majordomo/adapter/out/persistence/envoy/RubricRepositoryAdapterTest.java
git commit -m "add envoy Rubric JPA persistence adapter"
```

---

### Task 17: `JobPosting` JPA entity, mapper, repository, adapter

**Files:**
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/JobPostingEntity.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/JpaJobPostingRepository.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/JobPostingMapper.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/JobPostingRepositoryAdapter.java`
- Test: `src/test/java/com/majordomo/adapter/out/persistence/envoy/JobPostingRepositoryAdapterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.IntegrationTest;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class JobPostingRepositoryAdapterTest {

    @Autowired JobPostingRepositoryAdapter repo;

    @Test
    void roundTripsExtractedJsonMap() {
        var p = new JobPosting();
        p.setId(UuidFactory.newId());
        p.setSource("manual");
        p.setExternalId("ext-1");
        p.setCompany("Acme");
        p.setTitle("Senior");
        p.setRawText("body");
        p.setExtracted(Map.of("salary", "$200k-$250k", "remote", "yes"));
        p.setFetchedAt(Instant.now());

        repo.save(p);
        var loaded = repo.findById(p.getId()).orElseThrow();
        assertThat(loaded.getExtracted()).containsEntry("salary", "$200k-$250k");
        assertThat(loaded.getRawText()).isEqualTo("body");
    }

    @Test
    void findBySourceAndExternalIdDeduplicates() {
        var p = new JobPosting();
        p.setId(UuidFactory.newId());
        p.setSource("greenhouse");
        p.setExternalId("gh-42");
        p.setRawText("x");
        p.setFetchedAt(Instant.now());
        repo.save(p);

        assertThat(repo.findBySourceAndExternalId("greenhouse", "gh-42")).isPresent();
        assertThat(repo.findBySourceAndExternalId("greenhouse", "nope")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=JobPostingRepositoryAdapterTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `JobPostingEntity.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "envoy_job_posting")
public class JobPostingEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_id")
    private String externalId;

    private String company;
    private String title;
    private String location;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> extracted;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public Map<String, String> getExtracted() { return extracted; }
    public void setExtracted(Map<String, String> extracted) { this.extracted = extracted; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
```

- [ ] **Step 4: Create `JpaJobPostingRepository.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link JobPostingEntity}. */
public interface JpaJobPostingRepository extends JpaRepository<JobPostingEntity, UUID> {

    /** Unique lookup for dedup by source + externalId. */
    Optional<JobPostingEntity> findBySourceAndExternalId(String source, String externalId);
}
```

- [ ] **Step 5: Create `JobPostingMapper.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.envoy.JobPosting;

final class JobPostingMapper {

    private JobPostingMapper() { }

    static JobPostingEntity toEntity(JobPosting p) {
        var e = new JobPostingEntity();
        e.setId(p.getId());
        e.setSource(p.getSource());
        e.setExternalId(p.getExternalId());
        e.setCompany(p.getCompany());
        e.setTitle(p.getTitle());
        e.setLocation(p.getLocation());
        e.setRawText(p.getRawText());
        e.setExtracted(p.getExtracted());
        e.setFetchedAt(p.getFetchedAt());
        e.setArchivedAt(p.getArchivedAt());
        return e;
    }

    static JobPosting toDomain(JobPostingEntity e) {
        var p = new JobPosting();
        p.setId(e.getId());
        p.setSource(e.getSource());
        p.setExternalId(e.getExternalId());
        p.setCompany(e.getCompany());
        p.setTitle(e.getTitle());
        p.setLocation(e.getLocation());
        p.setRawText(e.getRawText());
        p.setExtracted(e.getExtracted());
        p.setFetchedAt(e.getFetchedAt());
        p.setArchivedAt(e.getArchivedAt());
        return p;
    }
}
```

- [ ] **Step 6: Create `JobPostingRepositoryAdapter.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!envoy-memory")
public class JobPostingRepositoryAdapter implements JobPostingRepository {

    private final JpaJobPostingRepository jpa;

    public JobPostingRepositoryAdapter(JpaJobPostingRepository jpa) { this.jpa = jpa; }

    @Override
    public JobPosting save(JobPosting posting) {
        return JobPostingMapper.toDomain(jpa.save(JobPostingMapper.toEntity(posting)));
    }

    @Override
    public Optional<JobPosting> findById(UUID id) {
        return jpa.findById(id).map(JobPostingMapper::toDomain);
    }

    @Override
    public Optional<JobPosting> findBySourceAndExternalId(String source, String externalId) {
        return jpa.findBySourceAndExternalId(source, externalId).map(JobPostingMapper::toDomain);
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./mvnw -Dtest=JobPostingRepositoryAdapterTest test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/majordomo/adapter/out/persistence/envoy/JobPosting* \
        src/main/java/com/majordomo/adapter/out/persistence/envoy/JpaJobPostingRepository.java \
        src/test/java/com/majordomo/adapter/out/persistence/envoy/JobPostingRepositoryAdapterTest.java
git commit -m "add envoy JobPosting JPA persistence adapter"
```

---

### Task 18: `ScoreReport` JPA entity, mapper, repository, adapter

**Files:**
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/ScoreReportEntity.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/JpaScoreReportRepository.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/ScoreReportMapper.java`
- Create: `src/main/java/com/majordomo/adapter/out/persistence/envoy/ScoreReportRepositoryAdapter.java`
- Test: `src/test/java/com/majordomo/adapter/out/persistence/envoy/ScoreReportRepositoryAdapterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.IntegrationTest;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ScoreReportRepositoryAdapterTest {

    @Autowired ScoreReportRepositoryAdapter repo;
    @Autowired JobPostingRepositoryAdapter postings;
    @Autowired RubricRepositoryAdapter rubrics;

    @Test
    void roundTripsScoreReport() {
        Rubric rubric = seedRubric();
        JobPosting posting = seedPosting();

        var report = new ScoreReport(UuidFactory.newId(), posting.getId(), rubric.id(), rubric.version(),
                Optional.empty(),
                List.of(new CategoryScore("c", 5, "Only", "rationale")),
                List.of(),
                5, 5, Recommendation.CONSIDER, "claude-sonnet-4-6", Instant.now());
        repo.save(report);

        var loaded = repo.findById(report.id()).orElseThrow();
        assertThat(loaded.finalScore()).isEqualTo(5);
        assertThat(loaded.categoryScores()).hasSize(1);
    }

    @Test
    void queryFiltersByMinFinalScoreAndRecommendation() {
        Rubric rubric = seedRubric();
        JobPosting posting = seedPosting();
        repo.save(fakeReport(posting, rubric, 80, Recommendation.APPLY_NOW));
        repo.save(fakeReport(posting, rubric, 50, Recommendation.CONSIDER));
        repo.save(fakeReport(posting, rubric, 20, Recommendation.SKIP));

        var page = repo.query(60, null, null, 100);
        assertThat(page.items()).allMatch(r -> r.finalScore() >= 60);

        var applyNowOnly = repo.query(null, Recommendation.APPLY_NOW, null, 100);
        assertThat(applyNowOnly.items())
                .allMatch(r -> r.recommendation() == Recommendation.APPLY_NOW);
    }

    private Rubric seedRubric() {
        var r = new Rubric(UuidFactory.newId(), 1, "sr-test",
                List.of(),
                List.of(new Category("c", "x", 5, List.of(new Tier("Only", 5, "x")))),
                List.of(), new Thresholds(70, 40, 20), Instant.now());
        return rubrics.save(r);
    }

    private JobPosting seedPosting() {
        var p = new JobPosting();
        p.setId(UuidFactory.newId());
        p.setSource("manual");
        p.setRawText("x");
        p.setExtracted(Map.of());
        p.setFetchedAt(Instant.now());
        return postings.save(p);
    }

    private ScoreReport fakeReport(JobPosting p, Rubric r, int score, Recommendation rec) {
        return new ScoreReport(UuidFactory.newId(), p.getId(), r.id(), r.version(),
                Optional.empty(), List.of(), List.of(), score, score, rec,
                "claude-sonnet-4-6", Instant.now());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=ScoreReportRepositoryAdapterTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `ScoreReportEntity.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.majordomo.domain.model.envoy.Recommendation;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "envoy_score_report")
public class ScoreReportEntity {

    @Id
    private UUID id;

    @Column(name = "posting_id", nullable = false)
    private UUID postingId;

    @Column(name = "rubric_id", nullable = false)
    private UUID rubricId;

    @Column(name = "rubric_version", nullable = false)
    private int rubricVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String body;

    @Column(name = "final_score", nullable = false)
    private int finalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recommendation recommendation;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPostingId() { return postingId; }
    public void setPostingId(UUID postingId) { this.postingId = postingId; }

    public UUID getRubricId() { return rubricId; }
    public void setRubricId(UUID rubricId) { this.rubricId = rubricId; }

    public int getRubricVersion() { return rubricVersion; }
    public void setRubricVersion(int rubricVersion) { this.rubricVersion = rubricVersion; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public int getFinalScore() { return finalScore; }
    public void setFinalScore(int finalScore) { this.finalScore = finalScore; }

    public Recommendation getRecommendation() { return recommendation; }
    public void setRecommendation(Recommendation recommendation) { this.recommendation = recommendation; }

    public Instant getScoredAt() { return scoredAt; }
    public void setScoredAt(Instant scoredAt) { this.scoredAt = scoredAt; }
}
```

- [ ] **Step 4: Create `JpaScoreReportRepository.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/** Spring Data repository for {@link ScoreReportEntity}. */
public interface JpaScoreReportRepository
        extends JpaRepository<ScoreReportEntity, UUID>,
                JpaSpecificationExecutor<ScoreReportEntity> { }
```

- [ ] **Step 5: Create `ScoreReportMapper.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.domain.model.envoy.ScoreReport;

final class ScoreReportMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScoreReportMapper() { }

    static ScoreReportEntity toEntity(ScoreReport r) {
        var e = new ScoreReportEntity();
        e.setId(r.id());
        e.setPostingId(r.postingId());
        e.setRubricId(r.rubricId());
        e.setRubricVersion(r.rubricVersion());
        e.setFinalScore(r.finalScore());
        e.setRecommendation(r.recommendation());
        e.setScoredAt(r.scoredAt());
        try {
            e.setBody(MAPPER.writeValueAsString(r));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialise ScoreReport", ex);
        }
        return e;
    }

    static ScoreReport toDomain(ScoreReportEntity e) {
        try {
            return MAPPER.readValue(e.getBody(), ScoreReport.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialise ScoreReport " + e.getId(), ex);
        }
    }
}
```

- [ ] **Step 6: Create `ScoreReportRepositoryAdapter.java`**

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.adapter.out.persistence.CursorSpecifications;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!envoy-memory")
public class ScoreReportRepositoryAdapter implements ScoreReportRepository {

    private final JpaScoreReportRepository jpa;

    public ScoreReportRepositoryAdapter(JpaScoreReportRepository jpa) { this.jpa = jpa; }

    @Override
    public ScoreReport save(ScoreReport report) {
        return ScoreReportMapper.toDomain(jpa.save(ScoreReportMapper.toEntity(report)));
    }

    @Override
    public Optional<ScoreReport> findById(UUID id) {
        return jpa.findById(id).map(ScoreReportMapper::toDomain);
    }

    @Override
    public Page<ScoreReport> query(Integer minFinalScore, Recommendation recommendation,
                                   UUID cursor, int limit) {
        int clamped = Math.max(1, Math.min(limit, 100));
        Specification<ScoreReportEntity> spec = Specification.where(
                        CursorSpecifications.<ScoreReportEntity>afterCursor(cursor))
                .and(minFinalScore == null ? null : (root, q, cb) ->
                        cb.greaterThanOrEqualTo(root.get("finalScore"), minFinalScore))
                .and(CursorSpecifications.fieldEquals("recommendation", recommendation));
        var page = jpa.findAll(spec, PageRequest.of(0, clamped + 1, Sort.by("id")));
        var items = page.stream().map(ScoreReportMapper::toDomain).toList();
        return Page.fromOverfetch(items, limit, ScoreReport::id);
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./mvnw -Dtest=ScoreReportRepositoryAdapterTest test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/majordomo/adapter/out/persistence/envoy/ScoreReport* \
        src/main/java/com/majordomo/adapter/out/persistence/envoy/JpaScoreReportRepository.java \
        src/test/java/com/majordomo/adapter/out/persistence/envoy/ScoreReportRepositoryAdapterTest.java
git commit -m "add envoy ScoreReport JPA persistence adapter"
```

---

### Task 19: Seed the default rubric via Flyway

**Files:**
- Create: `src/main/resources/envoy/default-rubric.json`
- Create: `src/main/resources/db/migration/V15__envoy_seed_default_rubric.sql`

- [ ] **Step 1: Create `default-rubric.json`**

This is the same rubric seeded by `InMemoryRubricRepository` in Task 13 — copy the structure out of that constructor into JSON. Save to `src/main/resources/envoy/default-rubric.json`:

```json
{
  "id": "01910000-0000-7000-8000-000000000001",
  "version": 1,
  "name": "default",
  "disqualifiers": [
    {"key": "ON_SITE_ONLY", "description": "Role requires on-site work only"},
    {"key": "NON_ENGINEERING", "description": "Posting is not an engineering role"}
  ],
  "categories": [
    {
      "key": "compensation", "description": "Base salary and equity", "maxPoints": 25,
      "tiers": [
        {"label": "Excellent", "points": 25, "criteria": "Base >$250k or total comp >$400k clearly stated"},
        {"label": "Good", "points": 18, "criteria": "Base $200-250k or range suggesting it"},
        {"label": "Fair", "points": 10, "criteria": "Base $150-200k"},
        {"label": "Poor", "points": 3, "criteria": "Below $150k or no range given"}
      ]
    },
    {
      "key": "remote", "description": "Remote flexibility", "maxPoints": 15,
      "tiers": [
        {"label": "Full remote", "points": 15, "criteria": "Fully remote, any US location"},
        {"label": "Hybrid", "points": 7, "criteria": "Some in-office days required"},
        {"label": "Regional remote", "points": 3, "criteria": "Remote but only from specific metros"}
      ]
    },
    {
      "key": "role_scope", "description": "Seniority and scope match", "maxPoints": 20,
      "tiers": [
        {"label": "Strong match", "points": 20, "criteria": "Staff/Principal level, backend or platform"},
        {"label": "Aligned", "points": 12, "criteria": "Senior engineer, clear backend work"},
        {"label": "Weak", "points": 4, "criteria": "Junior or unclear scope"}
      ]
    },
    {
      "key": "team_signals", "description": "Team/manager/culture signals", "maxPoints": 15,
      "tiers": [
        {"label": "Strong", "points": 15, "criteria": "Specific team, named manager, clear mandate"},
        {"label": "Generic", "points": 6, "criteria": "Typical posting language"},
        {"label": "Red", "points": 1, "criteria": "Mentions hustle culture, grind, 'rockstar' etc."}
      ]
    },
    {
      "key": "company_stage", "description": "Company stability & stage", "maxPoints": 15,
      "tiers": [
        {"label": "Mature", "points": 15, "criteria": "Public or late-stage with revenue"},
        {"label": "Growth", "points": 9, "criteria": "Series B/C/D with traction"},
        {"label": "Early", "points": 3, "criteria": "Seed/Series A"},
        {"label": "Risky", "points": 0, "criteria": "Pre-seed, no traction signals"}
      ]
    },
    {
      "key": "tech_stack", "description": "Tech stack alignment", "maxPoints": 10,
      "tiers": [
        {"label": "Perfect", "points": 10, "criteria": "Java/Spring, Postgres, cloud"},
        {"label": "Adjacent", "points": 5, "criteria": "Kotlin/Go/Python, same architecture space"},
        {"label": "Misaligned", "points": 1, "criteria": "Frontend-heavy or ecosystem mismatch"}
      ]
    }
  ],
  "flags": [
    {"key": "AT_WILL_EMPHASIS", "description": "Unusually aggressive at-will language", "penalty": 3},
    {"key": "UNPAID_TEST", "description": "Requires unpaid take-home >4 hours", "penalty": 5},
    {"key": "VAGUE_COMP", "description": "No compensation range given at all", "penalty": 2},
    {"key": "ON_CALL_HEAVY", "description": "Heavy on-call rotation explicitly required", "penalty": 2}
  ],
  "thresholds": {"applyImmediately": 75, "apply": 55, "considerOnly": 35},
  "effectiveFrom": "2026-04-23T00:00:00Z"
}
```

- [ ] **Step 2: Create `V15__envoy_seed_default_rubric.sql`**

Use Postgres' `pg_read_server_files` capability? No — simpler: inline the JSON in the migration so it works without filesystem reads. Paste the JSON from Step 1 between the single quotes, escaping single quotes as `''` if any appear (none do here).

```sql
-- System-default rubric (organization_id IS NULL). Visible to every org until
-- an org creates its own "default" via PUT /api/envoy/rubrics/default.
INSERT INTO envoy_rubric (id, organization_id, name, version, body, effective_from, created_by)
VALUES (
  '01910000-0000-7000-8000-000000000001',
  NULL,
  'default',
  1,
  $JSON${
  "id": "01910000-0000-7000-8000-000000000001",
  "version": 1,
  "name": "default",
  "disqualifiers": [
    {"key": "ON_SITE_ONLY", "description": "Role requires on-site work only"},
    {"key": "NON_ENGINEERING", "description": "Posting is not an engineering role"}
  ],
  "categories": [
    {"key": "compensation", "description": "Base salary and equity", "maxPoints": 25,
     "tiers": [
       {"label": "Excellent", "points": 25, "criteria": "Base >$250k or total comp >$400k clearly stated"},
       {"label": "Good", "points": 18, "criteria": "Base $200-250k or range suggesting it"},
       {"label": "Fair", "points": 10, "criteria": "Base $150-200k"},
       {"label": "Poor", "points": 3, "criteria": "Below $150k or no range given"}
     ]},
    {"key": "remote", "description": "Remote flexibility", "maxPoints": 15,
     "tiers": [
       {"label": "Full remote", "points": 15, "criteria": "Fully remote, any US location"},
       {"label": "Hybrid", "points": 7, "criteria": "Some in-office days required"},
       {"label": "Regional remote", "points": 3, "criteria": "Remote but only from specific metros"}
     ]},
    {"key": "role_scope", "description": "Seniority and scope match", "maxPoints": 20,
     "tiers": [
       {"label": "Strong match", "points": 20, "criteria": "Staff/Principal level, backend or platform"},
       {"label": "Aligned", "points": 12, "criteria": "Senior engineer, clear backend work"},
       {"label": "Weak", "points": 4, "criteria": "Junior or unclear scope"}
     ]},
    {"key": "team_signals", "description": "Team/manager/culture signals", "maxPoints": 15,
     "tiers": [
       {"label": "Strong", "points": 15, "criteria": "Specific team, named manager, clear mandate"},
       {"label": "Generic", "points": 6, "criteria": "Typical posting language"},
       {"label": "Red", "points": 1, "criteria": "Mentions hustle culture, grind, rockstar etc."}
     ]},
    {"key": "company_stage", "description": "Company stability & stage", "maxPoints": 15,
     "tiers": [
       {"label": "Mature", "points": 15, "criteria": "Public or late-stage with revenue"},
       {"label": "Growth", "points": 9, "criteria": "Series B/C/D with traction"},
       {"label": "Early", "points": 3, "criteria": "Seed/Series A"},
       {"label": "Risky", "points": 0, "criteria": "Pre-seed, no traction signals"}
     ]},
    {"key": "tech_stack", "description": "Tech stack alignment", "maxPoints": 10,
     "tiers": [
       {"label": "Perfect", "points": 10, "criteria": "Java/Spring, Postgres, cloud"},
       {"label": "Adjacent", "points": 5, "criteria": "Kotlin/Go/Python, same architecture space"},
       {"label": "Misaligned", "points": 1, "criteria": "Frontend-heavy or ecosystem mismatch"}
     ]}
  ],
  "flags": [
    {"key": "AT_WILL_EMPHASIS", "description": "Unusually aggressive at-will language", "penalty": 3},
    {"key": "UNPAID_TEST", "description": "Requires unpaid take-home >4 hours", "penalty": 5},
    {"key": "VAGUE_COMP", "description": "No compensation range given at all", "penalty": 2},
    {"key": "ON_CALL_HEAVY", "description": "Heavy on-call rotation explicitly required", "penalty": 2}
  ],
  "thresholds": {"applyImmediately": 75, "apply": 55, "considerOnly": 35},
  "effectiveFrom": "2026-04-23T00:00:00Z"
}$JSON$::jsonb,
  '2026-04-23T00:00:00Z',
  'seed'
);
```

- [ ] **Step 3: Write an integration test asserting the seed landed**

Create `src/test/java/com/majordomo/adapter/out/persistence/envoy/DefaultRubricSeedTest.java`:

```java
package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class DefaultRubricSeedTest {

    @Autowired RubricRepositoryAdapter repo;

    @Test
    void defaultRubricSeededAtV1() {
        var rubric = repo.findActiveByName("default").orElseThrow();
        assertThat(rubric.version()).isEqualTo(1);
        assertThat(rubric.categories()).hasSize(6);
        assertThat(rubric.thresholds().applyImmediately()).isEqualTo(75);
    }
}
```

- [ ] **Step 4: Run migration test**

Run: `./mvnw -Dtest=DefaultRubricSeedTest,FlywayMigrationIntegrationTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/envoy/ \
        src/main/resources/db/migration/V15__envoy_seed_default_rubric.sql \
        src/test/java/com/majordomo/adapter/out/persistence/envoy/DefaultRubricSeedTest.java
git commit -m "add envoy default rubric seed migration"
```

---

### Task 20: Remove in-memory repositories

**Files:**
- Delete: `src/main/java/com/majordomo/adapter/out/persistence/envoy/InMemoryRubricRepository.java`
- Delete: `src/main/java/com/majordomo/adapter/out/persistence/envoy/InMemoryJobPostingRepository.java`
- Delete: `src/main/java/com/majordomo/adapter/out/persistence/envoy/InMemoryScoreReportRepository.java`
- Modify: `src/test/java/com/majordomo/application/envoy/EnvoyVerticalSliceTest.java` — remove `"envoy-memory"` from `@ActiveProfiles`; it now runs against the JPA adapters via Testcontainers.

- [ ] **Step 1: Delete the in-memory files**

```bash
rm src/main/java/com/majordomo/adapter/out/persistence/envoy/InMemory*.java
```

- [ ] **Step 2: Drop the `@Profile("!envoy-memory")` guard on the three JPA adapters**

Remove the `@Profile("!envoy-memory")` annotation and its import from `RubricRepositoryAdapter`, `JobPostingRepositoryAdapter`, `ScoreReportRepositoryAdapter`.

- [ ] **Step 3: Update `EnvoyVerticalSliceTest`**

Change `@ActiveProfiles({"envoy-memory", "test"})` to `@ActiveProfiles("integration")` so the test runs against the real stack. (Adjust assertions if the seeded default rubric causes score differences — the stubbed LLM response in that test uses the same category keys as the seeded rubric, so it should pass.)

- [ ] **Step 4: Run the full build**

Run: `./mvnw verify`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add -u src/main/java/com/majordomo/adapter/out/persistence/envoy/ \
        src/test/java/com/majordomo/application/envoy/EnvoyVerticalSliceTest.java
git commit -m "remove envoy in-memory repositories and profile guards"
```

---

> **Phase 2 complete.** Envoy persists rubrics, postings, and score reports to PostgreSQL. The default rubric ships as a Flyway seed.

---

## Phase 3 — Additional Job Sources

**Goal:** Add `UrlFetchSource` (fetches arbitrary job URLs, hands HTML to the LLM for structured extraction) and `GreenhouseApiSource` (typed REST client against the public Greenhouse jobs API).

### Task 21: Shared HTTP client config for ingest

**Files:**
- Create: `src/main/java/com/majordomo/adapter/out/ingest/IngestHttpConfiguration.java`

- [ ] **Step 1: Create the config class**

```java
package com.majordomo.adapter.out.ingest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Shared {@link RestClient} used by all HTTP-based {@code JobSource} implementations.
 * Conservative timeouts — job boards can be slow, but we never want a single
 * posting fetch to block a thread for minutes.
 */
@Configuration
public class IngestHttpConfiguration {

    /** Shared client qualifier-named "ingestRestClient" for use in job sources. */
    @Bean("ingestRestClient")
    public RestClient ingestRestClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "majordomo-envoy/1.0")
                .build();
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./mvnw compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/majordomo/adapter/out/ingest/IngestHttpConfiguration.java
git commit -m "add envoy shared ingest RestClient"
```

---

### Task 22: `UrlFetchSource` with LLM-based HTML extraction

**Files:**
- Create: `src/main/java/com/majordomo/domain/port/out/envoy/LlmExtractionPort.java`
- Create: `src/main/java/com/majordomo/adapter/out/llm/AnthropicLlmExtractionAdapter.java`
- Create: `src/main/java/com/majordomo/adapter/out/ingest/UrlFetchSource.java`
- Test: `src/test/java/com/majordomo/adapter/out/ingest/UrlFetchSourceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.port.out.envoy.LlmExtractionPort;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrlFetchSourceTest {

    private MockWebServer web;
    private UrlFetchSource source;
    private LlmExtractionPort extractor;

    @BeforeEach
    void setUp() throws Exception {
        web = new MockWebServer();
        web.start();
        extractor = mock(LlmExtractionPort.class);
        source = new UrlFetchSource(RestClient.create(), extractor);
    }

    @AfterEach
    void tearDown() throws Exception { web.shutdown(); }

    @Test
    void supportsUrlType() {
        assertThat(source.supports(new JobSourceRequest("url", "x", Map.of()))).isTrue();
        assertThat(source.supports(new JobSourceRequest("manual", "x", Map.of()))).isFalse();
    }

    @Test
    void fetchesAndDelegatesExtractionToLlm() {
        web.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html><body>We pay well</body></html>"));
        when(extractor.extract(any())).thenReturn(Map.of(
                "company", "Acme", "title", "Senior", "salary", "$220k"));

        var req = new JobSourceRequest("url", web.url("/job/1").toString(), Map.of());
        JobPosting p = source.fetch(req);

        assertThat(p.getRawText()).contains("We pay well");
        assertThat(p.getExtracted()).containsEntry("company", "Acme");
        assertThat(p.getSource()).isEqualTo("url");
        assertThat(p.getExternalId()).isEqualTo(web.url("/job/1").toString());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=UrlFetchSourceTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `LlmExtractionPort.java`**

```java
package com.majordomo.domain.port.out.envoy;

import java.util.Map;

/**
 * Outbound port for LLM-driven structured extraction from free text (typically HTML).
 * Callers pass raw posting text; the port returns a flat map of labelled fields
 * (company, title, location, salary, etc.).
 */
public interface LlmExtractionPort {

    /**
     * Extracts structured fields from a posting's raw body. Keys in the returned map
     * are a best-effort subset of: {@code company}, {@code title}, {@code location},
     * {@code salary}, {@code equity}, {@code team}, {@code tech}.
     */
    Map<String, String> extract(String rawText);
}
```

- [ ] **Step 4: Create `AnthropicLlmExtractionAdapter.java`**

```java
package com.majordomo.adapter.out.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.domain.port.out.envoy.LlmExtractionPort;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Uses the shared {@link AnthropicMessageClient} to ask the LLM to pull structured
 * fields out of raw job-posting HTML or text.
 */
@Component
public class AnthropicLlmExtractionAdapter implements LlmExtractionPort {

    private static final String SYSTEM_PROMPT = """
            You will be given the raw body of a job posting (HTML or plain text).
            Return a JSON object with best-effort values for any of these keys you
            can determine: company, title, location, salary, equity, team, tech.
            Omit keys you cannot determine. Return JSON only, no prose, no fences.
            """;

    private final AnthropicMessageClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicLlmExtractionAdapter(AnthropicMessageClient client) { this.client = client; }

    @Override
    public Map<String, String> extract(String rawText) {
        String json = client.send(SYSTEM_PROMPT, rawText);
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            throw new LlmScoringException("Extraction LLM returned unparseable JSON: " + json, e);
        }
    }
}
```

- [ ] **Step 5: Create `UrlFetchSource.java`**

```java
package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.port.out.envoy.LlmExtractionPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Ingests a job posting from an arbitrary URL by fetching the HTML and handing
 * it to {@link LlmExtractionPort} for structured-field extraction. The raw HTML
 * is preserved on the posting's {@code rawText} so the scorer can still read the
 * full body.
 */
@Component
public class UrlFetchSource implements JobSource {

    private final RestClient http;
    private final LlmExtractionPort extractor;

    public UrlFetchSource(@Qualifier("ingestRestClient") RestClient http,
                          LlmExtractionPort extractor) {
        this.http = http;
        this.extractor = extractor;
    }

    @Override
    public String name() { return "url"; }

    @Override
    public boolean supports(JobSourceRequest request) {
        return "url".equals(request.type());
    }

    @Override
    public JobPosting fetch(JobSourceRequest request) {
        String url = request.payload();
        String body = http.get().uri(url).retrieve().body(String.class);
        Map<String, String> extracted = extractor.extract(body == null ? "" : body);

        var p = new JobPosting();
        p.setSource("url");
        p.setExternalId(url);
        p.setRawText(body == null ? "" : body);
        p.setCompany(extracted.get("company"));
        p.setTitle(extracted.get("title"));
        p.setLocation(extracted.get("location"));
        p.setExtracted(new HashMap<>(extracted));
        p.setFetchedAt(Instant.now());
        return p;
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./mvnw -Dtest=UrlFetchSourceTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/majordomo/domain/port/out/envoy/LlmExtractionPort.java \
        src/main/java/com/majordomo/adapter/out/llm/AnthropicLlmExtractionAdapter.java \
        src/main/java/com/majordomo/adapter/out/ingest/UrlFetchSource.java \
        src/test/java/com/majordomo/adapter/out/ingest/UrlFetchSourceTest.java
git commit -m "add envoy UrlFetchSource with LLM extraction"
```

---

### Task 23: `GreenhouseApiSource`

**Files:**
- Create: `src/main/java/com/majordomo/adapter/out/ingest/GreenhouseApiSource.java`
- Test: `src/test/java/com/majordomo/adapter/out/ingest/GreenhouseApiSourceTest.java`

Greenhouse's public job board API is `https://boards-api.greenhouse.io/v1/boards/{board}/jobs/{id}` and returns JSON with a `content` field (HTML). A `JobSourceRequest` of type `"greenhouse"` carries the board in `hints["board"]` and the job id in `payload`.

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GreenhouseApiSourceTest {

    private MockWebServer web;
    private GreenhouseApiSource source;

    @BeforeEach
    void setUp() throws Exception {
        web = new MockWebServer();
        web.start();
        var client = RestClient.builder().build();
        source = new GreenhouseApiSource(client, web.url("/").toString());
    }

    @AfterEach
    void tearDown() throws Exception { web.shutdown(); }

    @Test
    void supportsGreenhouseType() {
        assertThat(source.supports(new JobSourceRequest("greenhouse", "1", Map.of()))).isTrue();
    }

    @Test
    void mapsGreenhousePayloadToJobPosting() {
        web.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": 12345,
                          "title": "Senior Backend Engineer",
                          "location": {"name": "Remote - US"},
                          "content": "&lt;p&gt;We are hiring&lt;/p&gt;",
                          "company_name": "Acme"
                        }
                        """));

        var req = new JobSourceRequest("greenhouse", "12345", Map.of("board", "acme"));
        JobPosting p = source.fetch(req);

        assertThat(p.getSource()).isEqualTo("greenhouse");
        assertThat(p.getExternalId()).isEqualTo("12345");
        assertThat(p.getTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(p.getLocation()).isEqualTo("Remote - US");
        assertThat(p.getCompany()).isEqualTo("Acme");
        assertThat(p.getRawText()).contains("We are hiring");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=GreenhouseApiSourceTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `GreenhouseApiSource.java`**

```java
package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Ingests a posting from the public Greenhouse job board API. Requires
 * {@code hints["board"]} (the board slug) and {@code payload} (the job id).
 */
@Component
public class GreenhouseApiSource implements JobSource {

    private final RestClient http;
    private final String baseUrl;

    public GreenhouseApiSource(@Qualifier("ingestRestClient") RestClient http,
                               @Value("${envoy.greenhouse.base-url:https://boards-api.greenhouse.io}")
                               String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    @Override
    public String name() { return "greenhouse"; }

    @Override
    public boolean supports(JobSourceRequest request) {
        return "greenhouse".equals(request.type());
    }

    @Override
    public JobPosting fetch(JobSourceRequest request) {
        String board = request.hints() == null ? null : request.hints().get("board");
        if (board == null || request.payload() == null) {
            throw new IllegalArgumentException(
                    "greenhouse requires hints[\"board\"] and payload (job id)");
        }
        GhJob job = http.get()
                .uri(baseUrl + "/v1/boards/{board}/jobs/{id}", board, request.payload())
                .retrieve()
                .body(GhJob.class);
        if (job == null) {
            throw new IllegalStateException("Greenhouse returned empty body for " + request.payload());
        }

        var p = new JobPosting();
        p.setSource("greenhouse");
        p.setExternalId(String.valueOf(job.id()));
        p.setTitle(job.title());
        p.setLocation(job.location() == null ? null : job.location().name());
        p.setCompany(job.companyName());
        p.setRawText(stripHtml(job.content()));
        p.setExtracted(new HashMap<>(Map.of("board", board)));
        p.setFetchedAt(Instant.now());
        return p;
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Partial shape of Greenhouse's job-detail response. */
    private record GhJob(
            long id,
            String title,
            String content,
            GhLocation location,
            @com.fasterxml.jackson.annotation.JsonProperty("company_name") String companyName
    ) { }

    /** Greenhouse nested location. */
    private record GhLocation(String name) { }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -Dtest=GreenhouseApiSourceTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/majordomo/adapter/out/ingest/GreenhouseApiSource.java \
        src/test/java/com/majordomo/adapter/out/ingest/GreenhouseApiSourceTest.java
git commit -m "add envoy GreenhouseApiSource"
```

---

> **Phase 3 complete.** Envoy can ingest from manual paste, arbitrary URLs (with LLM extraction), or Greenhouse boards.

---

## Phase 4 — REST API, Querying, and Events

**Goal:** Expose envoy over HTTP under `/api/envoy`, add a rubric-management endpoint that appends new versions, and wire domain events into the existing `AuditEventListener`.

### Task 24: Query use case + service for score reports

**Files:**
- Create: `src/main/java/com/majordomo/domain/port/in/envoy/QueryScoreReportsUseCase.java`
- Create: `src/main/java/com/majordomo/application/envoy/ScoreReportQueryService.java`
- Test: `src/test/java/com/majordomo/application/envoy/ScoreReportQueryServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.application.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreReportQueryServiceTest {

    @Test
    void clampsLimitAndDelegatesToRepository() {
        var repo = mock(ScoreReportRepository.class);
        var service = new ScoreReportQueryService(repo);
        when(repo.query(any(), any(), any(), eq(100)))
                .thenReturn(new Page<>(List.of(), null, false));

        service.query(60, Recommendation.APPLY_NOW, null, 500);
        verify(repo).query(60, Recommendation.APPLY_NOW, null, 100);
    }

    @Test
    void findByIdDelegates() {
        var repo = mock(ScoreReportRepository.class);
        var expected = new ScoreReport(UuidFactory.newId(), UuidFactory.newId(),
                UuidFactory.newId(), 1, Optional.empty(), List.of(), List.of(),
                10, 10, Recommendation.CONSIDER, "m", Instant.now());
        when(repo.findById(expected.id())).thenReturn(Optional.of(expected));

        var service = new ScoreReportQueryService(repo);
        var found = service.findById(expected.id()).orElseThrow();
        assertThat(found.id()).isEqualTo(expected.id());
    }

    private static org.assertj.core.api.ObjectAssert<java.util.UUID> assertThat(java.util.UUID uuid) {
        return org.assertj.core.api.Assertions.assertThat(uuid);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=ScoreReportQueryServiceTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `QueryScoreReportsUseCase.java`**

```java
package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;

import java.util.Optional;
import java.util.UUID;

/** Inbound port for reading score reports. All operations are org-scoped. */
public interface QueryScoreReportsUseCase {

    /** Finds a report by id, scoped to an organization. */
    Optional<ScoreReport> findById(UUID id, UUID organizationId);

    /** Cursor-paginated, filterable report query within an org. */
    Page<ScoreReport> query(UUID organizationId, Integer minFinalScore,
                            Recommendation recommendation, UUID cursor, int limit);
}
```

- [ ] **Step 4: Create `ScoreReportQueryService.java`**

```java
package com.majordomo.application.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Read-only service for score reports. */
@Service
public class ScoreReportQueryService implements QueryScoreReportsUseCase {

    private final ScoreReportRepository repo;

    public ScoreReportQueryService(ScoreReportRepository repo) { this.repo = repo; }

    @Override
    public Optional<ScoreReport> findById(UUID id, UUID organizationId) {
        return repo.findById(id, organizationId);
    }

    @Override
    public Page<ScoreReport> query(UUID organizationId, Integer minFinalScore,
                                   Recommendation recommendation, UUID cursor, int limit) {
        int clamped = Math.max(1, Math.min(limit, 100));
        return repo.query(organizationId, minFinalScore, recommendation, cursor, clamped);
    }
}
```

- [ ] **Step 5: Run tests and commit**

Run: `./mvnw -Dtest=ScoreReportQueryServiceTest test`
Expected: PASS.

```bash
git add src/main/java/com/majordomo/domain/port/in/envoy/QueryScoreReportsUseCase.java \
        src/main/java/com/majordomo/application/envoy/ScoreReportQueryService.java \
        src/test/java/com/majordomo/application/envoy/ScoreReportQueryServiceTest.java
git commit -m "add envoy ScoreReportQueryService"
```

---

### Task 25: `PostingController` — ingestion + scoring endpoints

**Files:**
- Create: `src/main/java/com/majordomo/adapter/in/web/envoy/PostingController.java`
- Create: `src/main/java/com/majordomo/adapter/in/web/envoy/dto/IngestPostingRequest.java`
- Test: `src/test/java/com/majordomo/adapter/in/web/envoy/PostingControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.adapter.in.web.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.adapter.in.web.envoy.dto.IngestPostingRequest;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.*;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostingController.class)
@Import(SecurityConfig.class)
class PostingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean IngestJobPostingUseCase ingest;
    @MockitoBean ScoreJobPostingUseCase score;

    @Test
    @WithMockUser
    void ingestReturns201WithLocation() throws Exception {
        var saved = new JobPosting();
        saved.setId(UuidFactory.newId());
        saved.setSource("manual");
        saved.setRawText("body");
        when(ingest.ingest(any())).thenReturn(saved);

        var body = new IngestPostingRequest("manual", "body", Map.of());
        mvc.perform(post("/api/envoy/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    @WithMockUser
    void scoreReturnsReport() throws Exception {
        var postingId = UuidFactory.newId();
        var report = new ScoreReport(UuidFactory.newId(), postingId,
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 60, 60, Recommendation.APPLY,
                "claude-sonnet-4-6", Instant.now());
        when(score.score(eq(postingId), eq("default"))).thenReturn(report);

        mvc.perform(post("/api/envoy/postings/" + postingId + "/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalScore").value(60))
                .andExpect(jsonPath("$.recommendation").value("APPLY"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=PostingControllerTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `IngestPostingRequest.java`**

```java
package com.majordomo.adapter.in.web.envoy.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/** Request body for {@code POST /api/envoy/postings}. */
public record IngestPostingRequest(
        @NotBlank String type,
        @NotBlank String payload,
        Map<String, String> hints
) { }
```

- [ ] **Step 4: Create `PostingController.java`**

```java
package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.envoy.dto.IngestPostingRequest;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/** REST controller for ingesting and scoring job postings. */
@RestController
@RequestMapping("/api/envoy/postings")
@Tag(name = "Envoy", description = "Job posting ingestion and scoring")
public class PostingController {

    private final IngestJobPostingUseCase ingest;
    private final ScoreJobPostingUseCase score;
    private final OrganizationAccessService organizationAccessService;

    public PostingController(IngestJobPostingUseCase ingest,
                             ScoreJobPostingUseCase score,
                             OrganizationAccessService organizationAccessService) {
        this.ingest = ingest;
        this.score = score;
        this.organizationAccessService = organizationAccessService;
    }

    /** Ingests a posting from any supported source into the given org. */
    @PostMapping
    public ResponseEntity<JobPosting> ingest(
            @RequestParam UUID organizationId,
            @Valid @RequestBody IngestPostingRequest req) {
        organizationAccessService.verifyAccess(organizationId);
        Map<String, String> hints = req.hints() == null ? Map.of() : req.hints();
        JobPosting saved = ingest.ingest(
                new JobSourceRequest(req.type(), req.payload(), hints), organizationId);
        return ResponseEntity.created(URI.create("/api/envoy/postings/" + saved.getId())).body(saved);
    }

    /** Scores an existing posting against the active rubric for the org. */
    @PostMapping("/{id}/score")
    public ResponseEntity<ScoreReport> score(
            @PathVariable UUID id,
            @RequestParam UUID organizationId,
            @RequestParam(defaultValue = "default") String rubricName) {
        organizationAccessService.verifyAccess(organizationId);
        return ResponseEntity.ok(score.score(id, rubricName, organizationId));
    }
}
```

- [ ] **Step 5: Run tests and commit**

Run: `./mvnw -Dtest=PostingControllerTest test`
Expected: PASS.

```bash
git add src/main/java/com/majordomo/adapter/in/web/envoy/ \
        src/test/java/com/majordomo/adapter/in/web/envoy/PostingControllerTest.java
git commit -m "add envoy PostingController"
```

---

### Task 26: `ReportController` — paginated report query

**Files:**
- Create: `src/main/java/com/majordomo/adapter/in/web/envoy/ReportController.java`
- Test: `src/test/java/com/majordomo/adapter/in/web/envoy/ReportControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean QueryScoreReportsUseCase query;

    @Test
    @WithMockUser
    void listPassesFiltersThrough() throws Exception {
        when(query.query(any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(get("/api/envoy/reports")
                        .param("minFinalScore", "70")
                        .param("recommendation", "APPLY_NOW")
                        .param("limit", "25"))
                .andExpect(status().isOk());

        verify(query).query(eq(70), eq(Recommendation.APPLY_NOW), eq(null), eq(25));
    }

    @Test
    @WithMockUser
    void getByIdReturns200Or404() throws Exception {
        var id = UuidFactory.newId();
        var report = new ScoreReport(id, UuidFactory.newId(), UuidFactory.newId(), 1,
                Optional.empty(), List.of(), List.of(), 10, 10,
                Recommendation.CONSIDER, "m", Instant.now());
        when(query.findById(id)).thenReturn(Optional.of(report));

        mvc.perform(get("/api/envoy/reports/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        when(query.findById(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/envoy/reports/" + UuidFactory.newId()))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=ReportControllerTest test`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `ReportController.java`**

```java
package com.majordomo.adapter.in.web.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** REST controller for querying persisted score reports. */
@RestController
@RequestMapping("/api/envoy/reports")
@Tag(name = "Envoy", description = "Score report query")
public class ReportController {

    private final QueryScoreReportsUseCase query;
    private final OrganizationAccessService organizationAccessService;

    public ReportController(QueryScoreReportsUseCase query,
                            OrganizationAccessService organizationAccessService) {
        this.query = query;
        this.organizationAccessService = organizationAccessService;
    }

    /** Lists score reports for an org, cursor-paginated with optional filters. */
    @GetMapping
    public Page<ScoreReport> list(
            @RequestParam UUID organizationId,
            @RequestParam(required = false) Integer minFinalScore,
            @RequestParam(required = false) Recommendation recommendation,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int limit) {
        organizationAccessService.verifyAccess(organizationId);
        return query.query(organizationId, minFinalScore, recommendation, cursor, limit);
    }

    /** Fetches a single report by id within an org. */
    @GetMapping("/{id}")
    public ResponseEntity<ScoreReport> getById(@PathVariable UUID id,
                                               @RequestParam UUID organizationId) {
        organizationAccessService.verifyAccess(organizationId);
        return query.findById(id, organizationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 4: Run tests and commit**

Run: `./mvnw -Dtest=ReportControllerTest test`
Expected: PASS.

```bash
git add src/main/java/com/majordomo/adapter/in/web/envoy/ReportController.java \
        src/test/java/com/majordomo/adapter/in/web/envoy/ReportControllerTest.java
git commit -m "add envoy ReportController"
```

---

### Task 27: `RubricService` + `RubricController`

**Files:**
- Create: `src/main/java/com/majordomo/domain/port/in/envoy/ManageRubricUseCase.java`
- Create: `src/main/java/com/majordomo/application/envoy/RubricService.java`
- Create: `src/main/java/com/majordomo/adapter/in/web/envoy/RubricController.java`
- Test: `src/test/java/com/majordomo/application/envoy/RubricServiceTest.java`
- Test: `src/test/java/com/majordomo/adapter/in/web/envoy/RubricControllerTest.java`

`PUT /api/envoy/rubrics/{name}` creates a new version. Body is the rubric content minus `id`, `version`, and `effectiveFrom` — the service assigns those.

- [ ] **Step 1: Write the failing service test**

```java
package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.*;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RubricServiceTest {

    @Test
    void saveNewVersion_incrementsVersionAndStampsFields() {
        var repo = mock(RubricRepository.class);
        var existing = new Rubric(UuidFactory.newId(), 3, "default",
                List.of(), List.of(cat()), List.of(),
                new Thresholds(20, 15, 5), Instant.now().minusSeconds(3600));
        when(repo.findActiveByName("default")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var submitted = new Rubric(null, 0, "default", List.of(),
                List.of(cat()), List.of(), new Thresholds(30, 20, 10), null);

        var service = new RubricService(repo);
        Rubric saved = service.saveNewVersion("default", submitted);

        assertThat(saved.version()).isEqualTo(4);
        assertThat(saved.id()).isNotNull();
        assertThat(saved.effectiveFrom()).isNotNull();
        assertThat(saved.thresholds().applyImmediately()).isEqualTo(30);
    }

    @Test
    void saveNewVersion_startsAtV1WhenNoneExists() {
        var repo = mock(RubricRepository.class);
        when(repo.findActiveByName("brand-new")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var submitted = new Rubric(null, 0, "brand-new", List.of(),
                List.of(cat()), List.of(), new Thresholds(30, 20, 10), null);

        var saved = new RubricService(repo).saveNewVersion("brand-new", submitted);
        assertThat(saved.version()).isEqualTo(1);
    }

    private Category cat() {
        return new Category("c", "x", 10, List.of(new Tier("Only", 5, "x")));
    }
}
```

- [ ] **Step 2: Write the failing controller test**

```java
package com.majordomo.adapter.in.web.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.*;
import com.majordomo.domain.port.in.envoy.ManageRubricUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RubricController.class)
@Import(SecurityConfig.class)
class RubricControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean ManageRubricUseCase rubrics;

    @Test
    @WithMockUser
    void putRubric_createsNewVersion() throws Exception {
        var saved = new Rubric(UuidFactory.newId(), 4, "default",
                List.of(),
                List.of(new Category("c", "x", 10, List.of(new Tier("Only", 5, "x")))),
                List.of(), new Thresholds(20, 15, 5), Instant.now());
        when(rubrics.saveNewVersion(eq("default"), any())).thenReturn(saved);

        mvc.perform(put("/api/envoy/rubrics/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(saved)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw -Dtest=RubricServiceTest,RubricControllerTest test`
Expected: COMPILATION FAILURES.

- [ ] **Step 4: Create `ManageRubricUseCase.java`**

```java
package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.Rubric;

/** Inbound port for rubric authoring. */
public interface ManageRubricUseCase {

    /**
     * Appends a new org-specific version of the named rubric. The caller's {@code id},
     * {@code organizationId}, {@code version}, and {@code effectiveFrom} on {@code rubric}
     * are ignored — the service assigns them.
     *
     * @param name           the rubric name whose next version is being created
     * @param rubric         body for the new version (categories, flags, disqualifiers, thresholds)
     * @param organizationId the owning org
     * @return the saved rubric with its new id, organizationId, version, and effectiveFrom
     */
    Rubric saveNewVersion(String name, Rubric rubric, UUID organizationId);
}
```

- [ ] **Step 5: Create `RubricService.java`**

```java
package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.port.in.envoy.ManageRubricUseCase;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Rubric authoring — every save produces a new monotonically-increasing version. */
@Service
public class RubricService implements ManageRubricUseCase {

    private final RubricRepository repo;

    public RubricService(RubricRepository repo) { this.repo = repo; }

    @Override
    public Rubric saveNewVersion(String name, Rubric submitted, UUID organizationId) {
        int nextVersion = repo.findActiveByName(name, organizationId)
                .filter(r -> r.organizationId().isPresent())
                .map(r -> r.version() + 1)
                .orElse(1);
        Rubric toSave = new Rubric(
                UuidFactory.newId(),
                Optional.of(organizationId),
                nextVersion,
                name,
                submitted.disqualifiers(),
                submitted.categories(),
                submitted.flags(),
                submitted.thresholds(),
                Instant.now());
        return repo.save(toSave);
    }
}
```

- [ ] **Step 6: Create `RubricController.java`**

```java
package com.majordomo.adapter.in.web.envoy;

import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.port.in.envoy.ManageRubricUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for rubric authoring. */
@RestController
@RequestMapping("/api/envoy/rubrics")
@Tag(name = "Envoy", description = "Rubric management")
public class RubricController {

    private final ManageRubricUseCase rubrics;
    private final OrganizationAccessService organizationAccessService;

    public RubricController(ManageRubricUseCase rubrics,
                            OrganizationAccessService organizationAccessService) {
        this.rubrics = rubrics;
        this.organizationAccessService = organizationAccessService;
    }

    /** Appends a new org-specific version of the named rubric. */
    @PutMapping("/{name}")
    public Rubric putRubric(@PathVariable String name,
                            @RequestParam UUID organizationId,
                            @RequestBody Rubric rubric) {
        organizationAccessService.verifyAccess(organizationId);
        return rubrics.saveNewVersion(name, rubric, organizationId);
    }
}
```

- [ ] **Step 7: Run tests and commit**

Run: `./mvnw -Dtest=RubricServiceTest,RubricControllerTest test`
Expected: PASS.

```bash
git add src/main/java/com/majordomo/domain/port/in/envoy/ManageRubricUseCase.java \
        src/main/java/com/majordomo/application/envoy/RubricService.java \
        src/main/java/com/majordomo/adapter/in/web/envoy/RubricController.java \
        src/test/java/com/majordomo/application/envoy/RubricServiceTest.java \
        src/test/java/com/majordomo/adapter/in/web/envoy/RubricControllerTest.java
git commit -m "add envoy RubricService and RubricController"
```

---

### Task 28: Domain events + audit listener wiring

**Files:**
- Create: `src/main/java/com/majordomo/domain/model/event/JobPostingIngested.java`
- Create: `src/main/java/com/majordomo/domain/model/event/JobPostingScored.java`
- Modify: `src/main/java/com/majordomo/application/envoy/JobIngestionService.java` (publish `JobPostingIngested` after save)
- Modify: `src/main/java/com/majordomo/application/envoy/JobScorer.java` (publish `JobPostingScored` after save)
- Modify: `src/main/java/com/majordomo/adapter/in/event/AuditEventListener.java` (two new `@EventListener` methods)
- Modify: `src/main/java/com/majordomo/domain/model/EntityType.java` (add `SCORE_REPORT` if absent)
- Modify: tests — update `JobScorerTest` and `JobIngestionServiceTest` to verify `EventPublisher.publish(...)` is called.

- [ ] **Step 1: Create `JobPostingIngested.java`**

```java
package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a new job posting is persisted by the ingestion service.
 *
 * @param postingId      the newly-persisted posting's id
 * @param organizationId the owning organization
 * @param source         the {@code JobSource.name()} that produced it
 * @param occurredAt     when the event occurred
 */
public record JobPostingIngested(UUID postingId, UUID organizationId,
                                 String source, Instant occurredAt) { }
```

- [ ] **Step 2: Create `JobPostingScored.java`**

```java
package com.majordomo.domain.model.event;

import com.majordomo.domain.model.envoy.Recommendation;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a score report is persisted by {@code JobScorer}.
 *
 * @param reportId       the persisted report id
 * @param postingId      the posting that was scored
 * @param finalScore     the final score
 * @param recommendation the derived recommendation
 * @param occurredAt     when the event occurred
 */
public record JobPostingScored(
        UUID reportId,
        UUID organizationId,
        UUID postingId,
        int finalScore,
        Recommendation recommendation,
        Instant occurredAt) { }
```

- [ ] **Step 3: Wire `EventPublisher` into `JobIngestionService`**

Add constructor parameter `EventPublisher eventPublisher`, store as field. After the `postings.save(...)` line in `ingest(...)`, publish:

```java
eventPublisher.publish(new JobPostingIngested(saved.getId(), saved.getSource(), Instant.now()));
return saved;
```

(Rename the local `fetched` to `saved` after save, or inline — whichever reads cleanest.)

Update `JobIngestionServiceTest` to mock `EventPublisher` and `verify(eventPublisher).publish(any(JobPostingIngested.class))` in the happy-path test.

- [ ] **Step 4: Wire `EventPublisher` into `JobScorer`**

Same pattern. After `reports.save(report)` returns the persisted report, publish:

```java
eventPublisher.publish(new JobPostingScored(
        saved.id(), saved.postingId(), saved.finalScore(),
        saved.recommendation(), Instant.now()));
```

Update `JobScorerTest` to mock `EventPublisher` and verify the event is published.

- [ ] **Step 5: Add listener methods to `AuditEventListener`**

Open `src/main/java/com/majordomo/adapter/in/event/AuditEventListener.java` and add:

```java
@EventListener
public void onJobPostingIngested(JobPostingIngested event) {
    log(EntityType.JOB_POSTING.name(), event.postingId(),
        AuditAction.CREATE.name(), event.occurredAt());
}

@EventListener
public void onJobPostingScored(JobPostingScored event) {
    log(EntityType.SCORE_REPORT.name(), event.reportId(),
        AuditAction.CREATE.name(), event.occurredAt());
}
```

Import the two event records at the top. If `EntityType.SCORE_REPORT` doesn't exist, add it.

- [ ] **Step 6: Run the full build**

Run: `./mvnw verify`
Expected: BUILD SUCCESS — all tests pass, Checkstyle clean, ArchUnit clean.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/majordomo/domain/model/event/JobPosting*.java \
        src/main/java/com/majordomo/domain/model/EntityType.java \
        src/main/java/com/majordomo/application/envoy/JobIngestionService.java \
        src/main/java/com/majordomo/application/envoy/JobScorer.java \
        src/main/java/com/majordomo/adapter/in/event/AuditEventListener.java \
        src/test/java/com/majordomo/application/envoy/
git commit -m "add envoy domain events and audit listener wiring"
```

---

### Task 29: End-to-end HTTP integration test

**Files:**
- Create: `src/test/java/com/majordomo/adapter/in/web/envoy/EnvoyApiIntegrationTest.java`

- [ ] **Step 1: Write the test**

```java
package com.majordomo.adapter.in.web.envoy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.IntegrationTest;
import com.majordomo.adapter.in.web.envoy.dto.IngestPostingRequest;
import com.majordomo.adapter.out.llm.AnthropicMessageClient;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
class EnvoyApiIntegrationTest {

    private static MockWebServer llm;
    private final UUID orgId = UuidFactory.newId();

    @TestConfiguration
    static class Config {
        @Bean @Primary
        AnthropicMessageClient mockClient() throws Exception {
            llm = new MockWebServer();
            llm.start();
            AnthropicClient client = AnthropicOkHttpClient.builder()
                    .apiKey("test-key")
                    .baseUrl(llm.url("/").toString())
                    .build();
            return new AnthropicMessageClient(client, "claude-sonnet-4-6", 4096);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean OrganizationAccessService organizationAccessService;

    @BeforeEach
    void setUp() {
        doNothing().when(organizationAccessService).verifyAccess(any());
        llm.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"msg_01","type":"message","role":"assistant","model":"claude-sonnet-4-6","stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1},
                         "content":[{"type":"text","text":"{\\"disqualifierKey\\":null,\\"categoryVerdicts\\":[{\\"categoryKey\\":\\"compensation\\",\\"tierLabel\\":\\"Good\\",\\"rationale\\":\\"r\\"},{\\"categoryKey\\":\\"remote\\",\\"tierLabel\\":\\"Full remote\\",\\"rationale\\":\\"r\\"},{\\"categoryKey\\":\\"role_scope\\",\\"tierLabel\\":\\"Aligned\\",\\"rationale\\":\\"r\\"},{\\"categoryKey\\":\\"team_signals\\",\\"tierLabel\\":\\"Generic\\",\\"rationale\\":\\"r\\"},{\\"categoryKey\\":\\"company_stage\\",\\"tierLabel\\":\\"Growth\\",\\"rationale\\":\\"r\\"},{\\"categoryKey\\":\\"tech_stack\\",\\"tierLabel\\":\\"Perfect\\",\\"rationale\\":\\"r\\"}],\\"flagHits\\":[]}"}]}
                        """));
    }

    @AfterEach
    void tearDown() throws Exception { llm.shutdown(); }

    @Test
    @WithMockUser
    void ingestThenScore() throws Exception {
        var ingest = new IngestPostingRequest("manual",
                "Senior engineer at Acme, remote US, $220k base, Series C, Java/Spring",
                Map.of("company", "Acme", "title", "Senior Engineer"));
        var resp = mvc.perform(post("/api/envoy/postings")
                        .param("organizationId", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(ingest)))
                .andExpect(status().isCreated())
                .andReturn();

        JobPosting created = json.readValue(resp.getResponse().getContentAsString(), JobPosting.class);

        mvc.perform(post("/api/envoy/postings/" + created.getId() + "/score")
                        .param("organizationId", orgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").isNotEmpty())
                .andExpect(jsonPath("$.finalScore").isNumber());
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw -Dtest=EnvoyApiIntegrationTest test`
Expected: PASS.

- [ ] **Step 3: Run the full build**

Run: `./mvnw verify`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/majordomo/adapter/in/web/envoy/EnvoyApiIntegrationTest.java
git commit -m "add envoy end-to-end HTTP integration test"
```

---

### Task 30: Open the pull request

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/envoy
```

- [ ] **Step 2: Open PR against `main`**

```bash
gh pr create --title "add envoy: job search scoring service" --body "$(cat <<'EOF'
## Summary
- New `envoy` service — job posting ingestion and LLM-driven scoring against a versioned rubric
- Pluggable `JobSource` (manual paste, URL fetch, Greenhouse) via Spring discovery
- Rubrics stored as JSONB, versioned append-only; default rubric seeded via Flyway
- LLM returns tier selections (not point values); Java validates and computes
- Full REST API under `/api/envoy/...` with domain events wired into the audit listener

## Test plan
- [ ] `./mvnw verify` passes locally (Checkstyle + ArchUnit + all tests)
- [ ] Manually post a job, score it, check the report via the `/api/envoy/reports` endpoint
- [ ] Confirm the seeded default rubric is visible via `GET /api/envoy/reports` after a first scoring
EOF
)"
```

> The PR URL returned by `gh pr create` should be shared back to the requester.

---

## Phase 5 — Out of Scope for This Plan

Items deferred to a follow-up plan once Phase 4 is merged and in use:

- **Batch backlog rescoring** — admin endpoint to rescore N postings against the active rubric.
- **Notifications** — wire `JobPostingScored` events with `recommendation == APPLY_NOW` into the existing `Herald` notification stack.
- **Browsing UI / CLI** — Thymeleaf dashboard or CLI command for manual review.
- **Multi-rubric scoring** — score one posting against multiple rubrics (e.g. "ideal" vs "fallback").
- **Re-score on rubric change** — background job to rescore the open backlog when a rubric version changes.
- **Confidence per category** — extend `LlmScoreResponse` with a confidence score per category and flag low-confidence entries for human review.

These are deliberately held because each is a standalone feature with its own design choices (UI framework, notification cadence, background-job infrastructure). Building them inside this plan would inflate scope and delay shipping the vertical slice.

---

## Self-Review

**Spec coverage:**
- Domain records, enums, value objects — Tasks 2-6
- Pluggable ingestion (`JobSource`, `ManualPasteSource`, `UrlFetchSource`, `GreenhouseApiSource`, `JobIngestionService`) — Tasks 12, 22, 23
- Rubric persistence, versioning, effective-from — Tasks 16, 19, 27
- LLM scoring (`PromptBuilder`, `LlmScoringPort`, `AnthropicLlmScoringAdapter`, `ScoreAssembler`, `JobScorer`) — Tasks 8, 9, 10, 11
- Persistence (Flyway, entities, repositories, adapters) — Tasks 15, 16, 17, 18
- REST API (`/postings`, `/postings/{id}/score`, `/reports`, `/rubrics/{name}`) — Tasks 25, 26, 27
- Domain events + audit — Task 28
- Disqualifier short-circuit, flag penalties, threshold mapping — Task 8
- Reproducibility (rubric version + LLM model id on every report) — Task 4 + 11
- Out-of-scope items (batch rescore, notifications, UI, multi-rubric, confidence) — Phase 5 stub

**Placeholder scan:** no "TBD", no "handle appropriately", no "similar to Task N" — each step contains the concrete code or command needed. One externally-variable item remains: `V14` migration number (Task 15 Step 1 explicitly instructs the engineer to bump it if another migration landed during the branch's life).

**Type consistency:** `LlmScoringPort.score(JobPosting, Rubric)` matches the signature used in Task 10 and Task 11. `ScoreAssembler.assemble(JobPosting, Rubric, LlmScoreResponse, String)` matches Task 8 and Task 11. `Rubric` record constructor argument order is identical in Tasks 3, 13, 16, 19, 27. `ScoreReport` record shape is identical in Tasks 4, 8, 18, 27. The `LlmScoreResponse` JSON schema is the same across PromptBuilder output (Task 9), the adapter's parsing (Task 10), and the stubbed Anthropic responses in Tasks 14 and 29.

