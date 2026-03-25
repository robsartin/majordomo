# CLAUDE.md — Majordomo Project Guide

## Overview

Majordomo is a service-based personal information and property management system built with hexagonal architecture. Services are named after traditional household staff roles.

## Tech Stack

- **Java 25** (ADR-0005) — records, sealed classes, pattern matching, virtual threads
- **Spring Boot 3.5** (ADR-0006) — auto-config, Web, Data JPA, Security, Actuator
- **PostgreSQL 18** (ADR-0011) — arrays, JSONB, UUIDv7
- **Flyway** (ADR-0011) — forward-only versioned SQL migrations
- **Argon2id** (ADR-0016) — password hashing via Spring Security
- **Thymeleaf** — server-rendered pages (login, home)
- **SpringDoc OpenAPI** (ADR-0013) — auto-generated API docs at /swagger-ui.html
- **SLF4J** (ADR-0007) — logging API
- **Checkstyle** (ADR-0014) — Google-based style, enforced at build time

## Architecture (ADR-0002, ADR-0004)

Hexagonal (ports and adapters). Dependencies point inward.

```
domain/model/          — Pure domain classes, no framework deps
domain/port/in/        — Inbound port interfaces (use cases)
domain/port/out/       — Outbound port interfaces (repositories)
application/           — Use case implementations
adapter/in/web/        — REST controllers, Thymeleaf controllers
adapter/out/persistence/ — JPA entities, mappers, repository adapters
```

## Service Naming (ADR-0002)

| Service | Role | Package suffix |
|---------|------|---------------|
| The Steward | Property management | `steward` |
| The Concierge | Contact management | `concierge` |
| The Herald | Scheduling & notifications | `herald` |
| The Ledger | Finance & cost tracking | `ledger` |

## Development Workflow (ADR-0003, ADR-0010)

1. Branch from `main` — all work on feature branches
2. **Strict TDD**: write one failing test → make it pass → commit → refactor → commit
3. Submit PR when done — robsartin reviews before merge
4. No direct pushes to main

## Build Commands

```bash
./mvnw validate          # Checkstyle only
./mvnw compile           # Compile
./mvnw test              # Run tests
./mvnw verify            # Full build (compile + checkstyle + test)
./mvnw spring-boot:run   # Start app (requires PostgreSQL on localhost:5432)
```

## Conventions

- **Javadoc** (ADR-0015): Required on all public classes and methods. Getters/setters/entities exempt.
- **Mermaid diagrams** (ADR-0015): Use in docs and package-info.java where they aid understanding.
- **Soft delete**: Set `archived_at` timestamp, never hard delete.
- **UUIDv7**: All entity IDs. Time-sortable, used for cursor-based pagination.
- **API versioning** (ADR-0012): `X-API-Version` request header, defaults to latest.
- **Password hashing**: Argon2id only, no BCrypt.

## Running Locally

Requires PostgreSQL:
```bash
# With docker-compose (when available):
docker-compose up -d
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
