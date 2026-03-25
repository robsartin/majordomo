# 5. Use Java 25

Date: 2026-03-24

## Status

Accepted

## Context

Majordomo is a greenfield project with no legacy compatibility constraints. We are free to choose the latest Java version and take advantage of modern language features and runtime improvements.

Java 25 is the current release, offering virtual threads (Project Loom), pattern matching, record patterns, sealed classes, and other features that reduce boilerplate and improve readability.

## Decision

We will use Java 25 as the platform for all Majordomo services.

We will adopt modern language features where they improve clarity:

- **Records** for immutable data carriers (DTOs, domain value objects)
- **Sealed classes** for constrained type hierarchies in domain models
- **Pattern matching** (switch expressions, record patterns) for cleaner control flow
- **Virtual threads** for scalable concurrent I/O without reactive complexity

## Consequences

- Access to the latest language features keeps code concise and idiomatic.
- Virtual threads simplify concurrent service-to-service communication without requiring a reactive framework.
- All developers must be on Java 25; older JDKs will not compile the codebase.
- We will need to track the next LTS release and plan a migration when appropriate.
