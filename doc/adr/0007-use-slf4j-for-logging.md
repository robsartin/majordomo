# 7. Use SLF4J for logging

Date: 2026-03-24

## Status

Accepted

## Context

A service-based system needs consistent, structured logging across all services for debugging, auditing, and operational visibility. The logging API should be decoupled from the logging implementation so that the backend can be changed without modifying application code.

## Decision

We will use SLF4J as the logging API across all Majordomo services.

- All application code logs through the SLF4J API (`org.slf4j.Logger`).
- The logging backend (Logback, Log4j2, etc.) is chosen per deployment and configured via the adapter layer, not the domain.
- Spring Boot's default Logback backend is acceptable unless a specific need arises to switch.
- Structured logging (key-value pairs, MDC for correlation IDs) will be used to support log aggregation and searchability.

## Consequences

- Logging calls are implementation-agnostic — switching backends requires only a dependency and configuration change.
- Consistent logging API across all services simplifies debugging and log analysis.
- Domain code can use SLF4J without taking a dependency on any infrastructure framework.
- Developers must avoid using `System.out.println` or framework-specific logging APIs directly.
