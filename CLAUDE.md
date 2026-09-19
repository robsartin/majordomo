# CLAUDE.md — Majordomo Project Guide

## Overview

Majordomo is a service-based personal information and property management system built with hexagonal architecture. Services are named after traditional household staff roles.

## Tech Stack

- **Java 25** (ADR-0005) — records, sealed classes, pattern matching, virtual threads
- **Spring Boot 4.1** (ADR-0006) — auto-config, Web, Data JPA, Security, Actuator
- **PostgreSQL 18** (ADR-0011) — arrays, JSONB, UUIDv7
- **Redis 7** — cache layer (spring.cache.type=redis), TTL 5 min, key prefix `majordomo:`
- **Flyway** (ADR-0011) — forward-only versioned SQL migrations
- **Argon2id** (ADR-0016) — password hashing via Spring Security
- **Resilience4j** — circuit breaker and retry on notification sending
- **Thymeleaf + Tailwind CSS** (ADR-0019) — server-rendered pages (login, home, dashboard)
- **SpringDoc OpenAPI** (ADR-0013) — auto-generated API docs at /swagger-ui.html
- **SLF4J** (ADR-0007) — logging API
- **Checkstyle** (ADR-0014) — Google-based style, enforced at build time
- **ArchUnit** (ADR-0017) — architecture fitness functions enforced at test time
- **Apache PDFBox** (ADR-0026) — attachment text extraction for full-text search

## Architecture (ADR-0002, ADR-0004)

Hexagonal (ports and adapters). Dependencies point inward.

```
domain/model/            — Pure domain classes, no framework deps
domain/model/event/      — Domain events (records)
domain/port/in/          — Inbound port interfaces (use cases)
domain/port/out/         — Outbound port interfaces (repositories)
application/             — Use case implementations
adapter/in/web/          — REST controllers, Thymeleaf controllers
adapter/in/event/        — Spring event listeners (audit)
adapter/out/persistence/ — JPA entities, mappers, repository adapters
adapter/out/notification/ — Email/notification adapters (Resilience4j protected)
adapter/out/storage/     — File storage adapters (attachments)
adapter/out/event/       — Domain event publishers
```

## Service Naming (ADR-0002)

| Service | Role | Package suffix |
|---------|------|---------------|
| The Steward | Property management | `steward` |
| The Concierge | Contact management | `concierge` |
| The Herald | Scheduling & notifications | `herald` |
| The Ledger | Finance & cost tracking | `ledger` |
| The Envoy | Job-posting scoring & application support | `envoy` |
| The Librarian | Book catalog & enrichment | `librarian` |
| Identity | Users, auth, API keys | `identity` |
| The Dashboard | Aggregated summary | (top-level controllers) |

### Envoy routes (ADR-0022)

LLM-graded job-posting scoring against versioned rubrics. UI under `/envoy`,
JSON under `/api/envoy`.

| Route | Purpose |
|-------|---------|
| `GET /envoy` | List recent score reports for the user's org, with min-score / recommendation filters and an inline ingest+score form. |
| `POST /envoy` | Inline ingest a posting (manual paste, URL, Greenhouse) and score against the `default` rubric. |
| `GET /envoy/reports/{id}` | Detail view of a single report — categories, rationale, confidence, flags, LLM usage. |
| `GET /envoy/compare?ids=&rubric=` | Side-by-side comparison of 2–5 reports under one rubric. |
| `GET /envoy/rubrics`, `GET /envoy/rubrics/{name}/edit`, `POST /envoy/rubrics/{name}` | List, edit (form-based), and version rubrics. |
| `POST /api/envoy/postings`, `/{id}/score`, `/{id}/score-all`, `/rescore` | REST ingest, single-rubric score, multi-rubric score, batch rescore. |
| `GET /api/envoy/reports`, `GET /api/envoy/reports/{id}` | Cursor-paginated list and per-id fetch. |
| `PUT /api/envoy/rubrics/{name}` | Append a new org-specific rubric version. |

## Development Workflow (ADR-0003, ADR-0010)

1. Branch from `main` — all work on feature branches
2. **Strict TDD**: write one failing test → make it pass → commit → refactor → commit
3. Submit PR when done — robsartin reviews before merge
4. No direct pushes to main

## Build Commands

```bash
./mvnw validate                       # Checkstyle only
./mvnw compile                        # Compile
./mvnw test                           # Unit tests
./mvnw verify                         # Compile + checkstyle + unit tests + JaCoCo (unit only)
./mvnw -Pintegration-tests verify     # Above + Failsafe IT phase, JaCoCo merged report
./mvnw spring-boot:run                # Start app (requires PostgreSQL + Redis)
```

JaCoCo writes a coverage report to `target/site/jacoco/index.html` after `verify`. The default profile covers unit tests only. Activate the `integration-tests` profile (Docker required for Testcontainers) to also run `*IntegrationTest.java` via Failsafe; the report then reads a merged exec file (`jacoco-merged.exec`) that includes persistence-adapter coverage too.

## Known Trade-offs

- **Jakarta Validation in domain models**: Domain models (`Contact`, `Property`, `MaintenanceSchedule`, `ServiceRecord`) use `@NotBlank` and `@NotNull` from Jakarta Validation. Strictly, hexagonal architecture says domain should have zero framework dependencies. We accept this pragmatic trade-off because: (1) Jakarta Validation is a spec, not a framework implementation; (2) moving annotations to adapter-layer DTOs would duplicate every field definition; (3) the coupling is shallow — annotations are metadata only, with no behavioral dependency on a framework runtime. If this becomes problematic, extract validation to request DTOs in the adapter layer.

- **SHA-256 for API key hashing**: API keys use SHA-256 (not Argon2id) for fast O(1) lookup on every request. This is acceptable because API keys are high-entropy random values (32 bytes), unlike user-chosen passwords. Argon2id's memory-hard cost is unnecessary and would add latency to every authenticated API call.

## Conventions

- **Javadoc** (ADR-0015): Required on all public classes and methods. Getters/setters/entities exempt.
- **Mermaid diagrams** (ADR-0015): Use in docs and package-info.java where they aid understanding.
- **Soft delete**: Set `archived_at` timestamp, never hard delete.
- **Attachment search** (ADR-0026): a scheduled sweep extracts PDF/plain-text content into `attachments.extracted_text`; a generated `content_vector` joins property search. Images are `UNSUPPORTED` until OCR; scanned PDFs land as `EMPTY`, which is the queue for it.
- **UUIDv7** (ADR-0018): All entity IDs via `UuidFactory.newId()`. Time-sortable, used for cursor-based pagination. No `UUID.randomUUID()` in production code.
- **API versioning** (ADR-0012): `X-API-Version` request header, defaults to latest.
- **Password hashing**: Argon2id only, no BCrypt.
- **ArchUnit** (ADR-0017): Architecture fitness functions enforce hexagonal layer boundaries at test time.
- **Correlation IDs**: Every HTTP request gets an `X-Correlation-ID` header (generated if not provided). Included in all error responses and log entries via `CorrelationIdFilter`.
- **Resilience4j**: Circuit breaker and retry on notification adapter. Config in `application.yml` under `resilience4j:`.
- **Notification categories**: `MAINTENANCE_DUE`, `WARRANTY_EXPIRING`, `SITE_UPDATES`. Users can disable categories via `UserPreferences`.
- **Redis caching**: Dashboard summaries and spend calculations cached in Redis with 5-minute TTL. Cache evicted on domain events.
- **Test naming**: plain descriptive camelCase — no `should` prefix, no `@DisplayName`. Where a test names its subject use `<type>_<behaviour>` (`scoreReport_capturesAllAuditFields`, `book_rejectsBlankTitle`); otherwise a bare verb phrase (`throwsOnNon2xx`, `deserialisesFromCanonicalJson`).
- **Audit logging** (ADR-0020): All state-changing domain events produce `AuditLogEntry` records via `AuditEventListener`.

## Deployment

Self-hosted on a home server, reached over Tailscale (ADR-0024), with nightly
encrypted backups that verify their own restores (ADR-0025). See
`doc/deployment.md` and `doc/backup.md`. Segue must run on the same host — it is loopback-only, so
the Librarian's interest-graph sync cannot cross a machine boundary.

## Running Locally

Requires PostgreSQL and Redis:
```bash
docker compose up -d db redis    # PostgreSQL 18 + Redis 7
./mvnw spring-boot:run

# Access:
# App: http://localhost:8080
# Login: http://localhost:8080/login (robsartin / xyzzyPLAN9)
# Swagger: http://localhost:8080/swagger-ui.html
```

## ADR Index

See `doc/adr/` for all architecture decision records:

| # | Decision |
|---|----------|
| 0001 | Record architecture decisions |
| 0002 | Service-based architecture with household staff naming |
| 0003 | Strict test-driven development |
| 0004 | Hexagonal architecture for services |
| 0005 | Java 25 |
| 0006 | Latest Spring Boot |
| 0007 | SLF4J for logging |
| 0008 | Grafana for observability dashboards |
| 0009 | Prometheus for metrics collection |
| 0010 | Human review and branch-based workflow |
| 0011 | PostgreSQL 18 and Flyway |
| 0012 | Header-based API versioning |
| 0013 | SpringDoc OpenAPI |
| 0014 | Checkstyle for code style |
| 0015 | Javadoc on public methods, Mermaid diagrams |
| 0016 | Spring Security with form login, extensible to OAuth2 |
| 0017 | ArchUnit for architecture fitness functions |
| 0018 | UUIDv7 for entity identifiers |
| 0019 | Tailwind CSS for server-rendered UI |
| 0020 | Audit logging strategy |
| 0021 | Prefer test slices over @SpringBootTest |
| 0022 | Envoy: rubric-based job-posting scoring service |
| 0023 | Librarian: book catalog service with external enrichment |
| 0024 | Self-host majordomo on a home server, reached over Tailscale |
| 0025 | Encrypted, verified backups to a tailnet host and an object store |
| 0026 | Extract attachment text with PDFBox, on a scheduled sweep |
