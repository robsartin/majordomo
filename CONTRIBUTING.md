# Contributing to Majordomo

## Development Workflow

1. Branch from `main` — all work on feature branches
2. Follow strict TDD (ADR-0003): write one failing test → make it pass → commit → refactor → commit
3. Submit a PR when done — requires review before merge (ADR-0010)
4. No direct pushes to main

## Code Style

- Checkstyle enforced at build time (`./mvnw validate`)
- Google-based style rules (see `config/checkstyle/`)
- Javadoc required on all public classes and methods (ADR-0015)
- Mermaid diagrams where they aid understanding

## Architecture

- Hexagonal (ports and adapters) — see ADR-0004
- Dependencies point inward: adapter → port → domain
- ArchUnit tests enforce boundaries
- UUIDv7 for all entity IDs via `UuidFactory.newId()`
- No `UUID.randomUUID()` in production code

## Adding a New Service

1. Create packages: `domain/model/<service>`, `domain/port/in/<service>`, `domain/port/out/<service>`, `application/<service>`, `adapter/in/web/<service>`, `adapter/out/persistence/<service>`
2. Follow the household staff naming convention (ADR-0002)
3. Create inbound port, application service, REST controller, JPA entities/adapters
4. Add OpenAPI `@Tag` to controller
5. Add group to `OpenApiConfig`

## Build Commands

```bash
./mvnw validate    # Checkstyle
./mvnw test        # Unit tests
./mvnw verify      # Full build
```

## Running Locally

```bash
docker-compose up -d   # PostgreSQL + Redis
./mvnw spring-boot:run
# http://localhost:8080 (robsartin / xyzzyPLAN9)
```
