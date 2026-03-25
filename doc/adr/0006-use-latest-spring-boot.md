# 6. Use latest Spring Boot

Date: 2026-03-24

## Status

Accepted

## Context

Each Majordomo service needs a framework for dependency injection, configuration, HTTP/REST support, and integration with infrastructure (databases, messaging, metrics). We need a framework that is well-supported, widely understood, and compatible with Java 25 and hexagonal architecture (see ADR-0004, ADR-0005).

## Decision

We will use the latest stable release of Spring Boot as the application framework for all services.

Spring Boot provides:

- Auto-configuration and convention-over-configuration for fast service setup
- Spring Web for REST adapters (inbound) and RestClient/WebClient for outbound adapters
- Spring Data for repository adapters
- Actuator for health checks, metrics, and operational endpoints
- Native support for virtual threads (Java 25)
- Micrometer integration for Prometheus metrics (see ADR-0009)

We will stay on the latest stable release and upgrade promptly when new versions are available.

## Consequences

- Rapid bootstrapping of new services with minimal configuration.
- Large ecosystem of starters and integrations reduces custom infrastructure code.
- The Spring community and documentation make onboarding straightforward.
- Spring Boot's opinions may occasionally conflict with hexagonal architecture purity; adapters should wrap Spring concerns rather than letting Spring annotations leak into the domain layer.
- Staying on the latest release requires regular dependency updates, but avoids accumulating upgrade debt.
