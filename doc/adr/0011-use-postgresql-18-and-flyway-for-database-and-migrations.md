# 11. Use PostgreSQL 18 and Flyway for database and migrations

Date: 2026-03-24

## Status

Accepted

## Context

Majordomo's domain model includes structured entities with relationships (properties, contacts, schedules, service records) that map naturally to a relational schema. We need a database that handles relational data well, supports JSON for flexible fields (vCard arrays), and is production-proven. We also need a reliable, version-controlled approach to schema evolution as the model grows.

## Decision

We will use **PostgreSQL 18** as the relational database for all Majordomo services.

We will use **Flyway** for schema migration management.

- All schema changes are expressed as versioned SQL migration files (e.g., `V1__initial_schema.sql`).
- Migrations are stored in `src/main/resources/db/migration/` and version-controlled with the application code.
- Flyway runs automatically on application startup via Spring Boot integration.
- Migrations are forward-only — no undo migrations. Fixes are applied as new versioned migrations.
- PostgreSQL arrays and JSONB are available for multi-value fields (emails, telephones, nicknames, urls) without requiring separate join tables for simple lists.

## Consequences

- PostgreSQL's mature feature set (arrays, JSONB, UUIDv7 support, full-text search) fits the domain model well.
- Flyway ensures schema changes are repeatable, auditable, and applied consistently across environments.
- Schema history is visible in version control alongside the code that depends on it.
- All environments (dev, test, prod) stay in sync — no manual DDL.
- Developers must write migrations carefully since they are forward-only; destructive changes require a planned migration path.
