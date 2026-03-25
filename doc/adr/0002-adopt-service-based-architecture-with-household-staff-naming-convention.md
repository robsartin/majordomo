# 2. Adopt service-based architecture with household staff naming convention

Date: 2026-03-24

## Status

Accepted

## Context

Majordomo combines personal information management (PIM) with physical property management. These domains span contacts, calendars, asset tracking, maintenance scheduling, and financial records. Building this as a monolith would tightly couple concerns that naturally evolve at different rates and have different scaling characteristics.

The project name "Majordomo" — the head of a household — suggests a thematic naming convention that maps naturally to a service-based architecture where each service plays a distinct household role.

## Decision

We will build Majordomo as a collection of independent services, each responsible for a single domain. The orchestration layer is "Majordomo" itself, and each service is named after a traditional estate household role:

- **The Steward** (Property Service) — manages physical assets, their state, and documentation (manuals, receipts)
- **The Concierge** (Contact Service) — manages relationships with vendors, maintenance professionals, and sellers
- **The Herald** (Calendar/Notification Service) — handles scheduling, reminders, and warranty expirations
- **The Ledger** (Finance Service) — tracks costs from purchase price to lifetime maintenance spend

New services follow this naming convention (e.g., "The Gardener" for landscaping, "The Archivist" for document management).

Services communicate through well-defined interfaces. Each service can be developed, tested, and deployed independently.

## Consequences

- Each service can be added incrementally — we do not need all services on day one.
- Services can be built with different technologies if appropriate, though consistency is preferred.
- The naming convention makes the system intuitive and keeps architectural discussions grounded in clear metaphors.
- Cross-service operations (e.g., scheduling a maintenance event that involves a contact, an asset, and a calendar entry) require coordination through the orchestration layer, adding complexity compared to a monolith.
- Service boundaries must be carefully defined to avoid tight coupling or excessive inter-service chatter.
