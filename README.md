# Majordomo

A service-based personal information and property management system. Majordomo acts as the head of household — it knows where everything is, who sold it, and who is supposed to fix it.

## Vision

Majordomo bridges the gap between digital data (PIM) and physical assets (property management) through a modular, service-oriented architecture. Rather than treating belongings as static inventory, Majordomo orchestrates the relationships between people, things, and time.

An item isn't just a "thing you own" — it's a nexus of relationships: the vendor who sold it, the technician who maintains it, and the future you who needs it to work.

## Architecture

Majordomo is built as a collection of independent services, each named after a role within a traditional estate household:

| Service | Role | Responsibility |
|---------|------|----------------|
| **The Steward** | Property Service | Manages physical assets, their current state, parent/child hierarchy, and documentation (manuals, receipts, attachments) |
| **The Concierge** | Contact Service | Manages relationships — vendors, maintenance professionals, sellers — and links them to properties |
| **The Herald** | Calendar/Notification Service | Maintenance schedules, service-record history, due-date reminders, warranty expirations |
| **The Ledger** | Finance Service | Tracks costs from purchase price to lifetime maintenance spend; per-property and org-level rollups |
| **The Envoy** | Job-Posting Scoring (ADR-0022) | LLM-graded scoring of job postings against versioned rubrics; ingest from manual paste, URL, or Greenhouse |
| **Identity** | User/Auth Service | Users, organizations, memberships, API keys, OAuth links |
| **The Dashboard** | Summary Service | Aggregated overview — properties, contacts, upcoming maintenance, total/projected spend, recent apply-now postings |

The **Majordomo** itself is the orchestration layer that ties these services together.

Additional services can be introduced over time (e.g., "The Gardener" for landscaping, "The Archivist" for digital documents) without breaking the naming or architectural model.

## Key Concepts

- **Service-based**: Each domain is an independent service that can be developed, deployed, and scaled on its own
- **Relationship-first**: The system models connections between people, assets, and events — not just static records
- **Lifecycle-aware**: Assets are tracked from acquisition through maintenance to eventual replacement
- **Cursor pagination**: All list endpoints use UUIDv7-based cursor pagination for stable, efficient paging (see [doc/pagination.md](doc/pagination.md))
- **Search and filtering**: Properties and contacts support search by name, category, status, and more
- **File attachments**: Properties and service records support file uploads with primary/sort-order metadata
- **Notifications**: Scheduled maintenance and warranty reminders via email, with per-user category preferences (see [doc/notifications.md](doc/notifications.md))
- **Audit log**: All state-changing domain events produce durable, queryable audit trail entries (ADR-0020)

## Authentication

Majordomo uses Spring Security for authentication with multiple methods:

- **Form login** at `/login` — username/password with Argon2id hashing
- **OAuth2 Google** — login via Google account, linked to Majordomo user via `OAuthLink` entity
- **API keys** — machine-to-machine authentication via `X-API-Key` header (see [doc/api-keys.md](doc/api-keys.md))

Passwords are hashed using Argon2id, the Password Hashing Competition winner, providing strong
resistance to GPU and ASIC attacks.

The authentication layer follows the hexagonal architecture: `AuthenticationService` implements
Spring Security's `UserDetailsService` interface, bridging Spring Security to the domain's
`UserRepository` and `CredentialRepository` ports. Spring Security concerns remain in the
adapter layer — the domain model has no dependency on Spring Security.

For detailed developer documentation, see [doc/authentication.md](doc/authentication.md).

## Getting Started

```bash
# Start PostgreSQL and Redis
docker-compose up -d

# Run the application (Java 25 required)
./mvnw spring-boot:run

# Access
# App:      http://localhost:8080
# Login:    http://localhost:8080/login  (robsartin / xyzzyPLAN9)
# Swagger:  http://localhost:8080/swagger-ui.html
```

To stop:
```bash
docker-compose down        # Stop containers (keep data)
docker-compose down -v     # Stop and remove data volume
```

If your shell defaults to JDK 17 or earlier (e.g. via SDKMAN), point Maven at a JDK 25 install:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
./mvnw verify -DskipITs
```

## For users

- **[doc/user-guide/](doc/user-guide/index.md)** — feature tour: properties, contacts, schedules, ledger, envoy, audit, API keys.

## For developers

- **[CLAUDE.md](CLAUDE.md)** — full developer guide: tech stack, ADR index, conventions, build commands.
- **[doc/architecture.md](doc/architecture.md)** — top-down view of services, ports, adapters, and request flow.
- **[doc/development.md](doc/development.md)** — build commands, test conventions, pre-PR checklist.
- **[doc/domain-model.md](doc/domain-model.md)** — aggregate diagrams.
- **[doc/adr/](doc/adr/)** — every architectural decision, numbered.

## Status

Active development. Core services (Steward, Concierge, Herald, Ledger, Envoy, Identity) and their web UIs are operational. Architecture decisions recorded in `doc/adr/`.

## License

See [LICENSE](LICENSE).
