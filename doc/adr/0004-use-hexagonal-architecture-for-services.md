# 4. Use hexagonal architecture for services

Date: 2026-03-24

## Status

Accepted

## Context

Each Majordomo service (Steward, Concierge, Herald, Ledger) must be independently developable and testable (see ADR-0002). We also practice strict TDD (see ADR-0003), which requires that core business logic be testable without external dependencies like databases, HTTP servers, or third-party APIs.

We need an internal structure for each service that enforces a clear separation between domain logic and infrastructure concerns.

## Decision

Each service will follow hexagonal architecture (ports and adapters), as described by Alistair Cockburn.

The structure has three layers:

- **Domain** — Pure business logic and domain models. No dependencies on frameworks, databases, or external services. This is where TDD lives most naturally.
- **Ports** — Interfaces that define how the domain interacts with the outside world. Inbound ports (driving) describe what the application can do. Outbound ports (driven) describe what the application needs from external systems.
- **Adapters** — Concrete implementations of ports. Inbound adapters (e.g., REST controllers, CLI handlers, message consumers) translate external requests into domain operations. Outbound adapters (e.g., database repositories, API clients, message producers) fulfill the domain's infrastructure needs.

Dependencies always point inward: adapters depend on ports, ports depend on the domain. The domain depends on nothing.

## Consequences

- Domain logic is fully testable with fast, isolated unit tests — no mocks of infrastructure required at the domain level.
- Adapters can be swapped without changing business logic (e.g., replacing a SQL repository with an in-memory one for testing, or switching from REST to gRPC).
- Each service has a consistent internal structure, making it easier to navigate and onboard to any service in the system.
- The port/adapter boundary adds some boilerplate (interfaces, adapter classes) that may feel heavy for very simple services.
- Aligns well with TDD: tests drive the domain design through ports, and adapters are integration-tested separately.
