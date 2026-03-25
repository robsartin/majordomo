# 17. Use ArchUnit for architecture fitness functions

Date: 2026-03-25

## Status

Accepted

## Context

Architecture rules documented in ADRs (hexagonal layers, naming conventions, dependency direction) are enforced only through code review. Automated fitness functions catch violations at build time, preventing architectural drift.

## Decision

We will use ArchUnit to enforce architecture rules as JUnit tests that run during the Maven test phase.

Rules enforced:
- Domain layer (`domain.model`, `domain.port`) must not import Spring, JPA, or Hibernate packages (Jakarta Validation exempted per CLAUDE.md trade-off)
- Application layer must not import adapter packages
- Adapters must not import other adapter packages
- Controllers must depend on inbound ports, not outbound ports directly
- Naming conventions: entities end with Entity, mappers with Mapper, adapters with Adapter/Controller/Config

## Consequences

- Architecture violations fail the build immediately
- New developers get instant feedback on structural mistakes
- Rules are version-controlled and evolve with the architecture
- Some rules may need exceptions as pragmatic trade-offs arise
