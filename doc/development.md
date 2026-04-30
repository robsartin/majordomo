# Development

This is the day-to-day reference. For the wide context (tech stack, ADR
index, architecture rules), see [CLAUDE.md](../CLAUDE.md) and
[architecture.md](architecture.md).

## Prerequisites

- **Java 25** — required by `pom.xml`. If your shell defaults to a lower
  JDK (SDKMAN often points to 17 or 21), point Maven at a JDK 25 install:
  ```bash
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
  ```
- **Docker** — for local PostgreSQL 18 + Redis 7 (`docker-compose up -d`)
  and for Testcontainers (integration tests).
- **Maven 3.9+** — provided via `./mvnw` wrapper.

## Build commands

| Command | What it does |
|---|---|
| `./mvnw validate` | Checkstyle only — fast static check |
| `./mvnw compile` | Compile main sources |
| `./mvnw test` | Surefire unit tests (excludes `*IntegrationTest.java`) |
| `./mvnw verify -DskipITs` | Compile + checkstyle + Surefire + JaCoCo (unit only) |
| `./mvnw -Pintegration-tests verify` | Adds Failsafe integration tests (Testcontainers Postgres) |
| `./mvnw spring-boot:run` | Starts the app; needs PostgreSQL + Redis up |

JaCoCo reports land at `target/site/jacoco/index.html` after `verify`.

## Test conventions (ADR-0021)

- **Slice tests are the default.** Use `@WebMvcTest(SomeController.class)`
  for controller behavior and `@DataJpaTest` for repository adapters. Mock
  the inbound ports directly with `@MockitoBean`.
- **`@SpringBootTest` is reserved** for end-to-end pipeline coverage and
  smoke tests. The vertical-slice tests that need it are named
  `*IntegrationTest.java` so they only run under the integration-tests
  Maven profile (Testcontainers Postgres).
- **Test method names** follow `behaviorWhenCondition` style (e.g.
  `editFormReturns403WhenCrossOrg`). Each test has a one-line Javadoc
  describing intent.
- **TDD is mandatory** (ADR-0003): write a failing test, watch it RED,
  implement the minimum to make it GREEN, commit, refactor, commit.

## Pre-PR checklist

Run **all** of these before declaring a PR ready:

1. `./mvnw verify -DskipITs` — green locally (use the JAVA_HOME export above).
2. `git push -u origin <branch>` — push the branch.
3. `gh pr create --title ... --body ...` — open the PR.
4. `gh pr checks <PR#>` — wait until **all four checks pass**:
   - `Analyze (java-kotlin)` — CodeQL build
   - `CodeQL` — security findings (this is the silent killer; e.g. ReDoS,
     SSRF, command injection — fixes typically a regex or boundary check)
   - `build` — Spring Boot CI
   - `checkstyle` — style enforcement
5. Only then ping for review.

If any check fails, fix and push again before the PR is reviewable.
Local checkstyle and tests don't catch CodeQL findings.

## Conventions you'll bump into

- **File length budget**: 500 lines per file (Checkstyle). When a
  controller crosses, split a sibling for orthogonal routes (see
  `PropertyContactLinkController`).
- **Line length budget**: 120 characters.
- **Javadoc**: required on public classes and methods (ADR-0015).
  Getters / setters / JPA entities / mappers are exempt via
  `config/checkstyle/suppressions.xml`.
- **UUIDs**: always `UuidFactory.newId()`. Never `UUID.randomUUID()` in
  production code — ArchUnit will fail the build (#249).
- **Soft delete**: set `archivedAt` to `Instant.now()`. No hard deletes
  except in tests.
- **Form helpers**: pull `blankToNull`, `splitLines`, `parseUuid`, and
  `parsePrice` from `com.majordomo.adapter.in.web.FormBindingHelper`
  rather than re-rolling them.
- **Cross-org access**: every page handler that loads a foreign entity
  must call `OrganizationAccessService.verifyAccess(entity.getOrganizationId())`.
  Surface 403 via `AccessDeniedException` (`GlobalExceptionHandler`
  maps it).
- **Migrations**: forward-only, numbered `V<next>__description.sql`.
  Never edit a previously-shipped file.

## Working with the LLM-driven services

Envoy uses a real Anthropic client in production. For local dev:

- Set `ANTHROPIC_API_KEY` in your environment, or
- Run with profile `local-mock` (if present) which stubs the LLM.

Tests mock `AnthropicMessageClient` — see `EnvoyVerticalSliceIntegrationTest`
for the pattern.

## Common gotchas

- **Hibernate's H2 schema generation rejects `text[]` arrays.** Anything
  hitting `@SpringBootTest` against H2 will see this; rename to
  `*IntegrationTest.java` to route through Failsafe + Postgres
  Testcontainers (ADR-0011).
- **`@WebMvcTest` slice tests** must declare `@MockitoBean` for every
  bean the controller's constructor injects, including `ApiKeyRepository`
  and `OAuth2UserService` because the security filter chain reaches them.
- **`@AuthenticationPrincipal UserDetails principal`** in Spring tests
  pairs with `@WithMockUser(username = "...")` — the principal's
  `getUsername()` is what `CurrentOrganizationResolver` resolves on.
