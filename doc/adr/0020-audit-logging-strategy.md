# 20. Audit logging strategy

Date: 2026-03-25

## Status

Accepted

## Context

Majordomo manages properties, service records, users, and organizations on behalf of its users. For compliance, debugging, and user trust we need an immutable record of "who changed what, when." Today domain events are published (e.g. `ServiceRecordCreated`, `PropertyArchived`, `UserCreated`) but they are only logged to SLF4J and used for cache eviction — there is no durable, queryable audit trail.

Requirements:
- Every state-changing domain event must produce an audit log entry.
- Entries must capture entity type, entity ID, action, acting user (when known), timestamp, and an optional JSON diff of the change.
- The audit log must be queryable by entity and by user.
- The solution must work within the existing hexagonal architecture and Spring event system.

## Decision

We will introduce an **audit_log** table and a corresponding domain model, outbound port, persistence adapter, and event listener.

### Storage

A Flyway migration creates the `audit_log` table:

```sql
CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   UUID         NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    user_id     UUID,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    diff_json   TEXT
);
```

Indexes on `(entity_type, entity_id)`, `user_id`, and `occurred_at` support the expected query patterns.

### Domain model

`AuditLogEntry` is a plain domain class in `domain.model` with no framework dependencies.

### Outbound port

`AuditLogRepository` (in `domain.port.out`) exposes `save`, `findByEntityTypeAndEntityId`, and `findByUserId`.

### Persistence adapter

A JPA entity, mapper, and repository adapter in `adapter.out.persistence.audit` implement the port, following the same pattern as `AttachmentRepositoryAdapter`.

### Event listener

`AuditEventListener` (in `adapter.in.event`) subscribes to all domain events via `@EventListener` and writes audit log entries through the outbound port. This keeps audit concerns in the adapter layer — the domain services are unaware of auditing.

### REST API

`AuditController` exposes:
- `GET /api/audit?entityType=&entityId=` — query by entity
- `GET /api/audit/organizations/{orgId}` — org-wide activity feed

## Consequences

- Every domain event automatically produces a durable audit record.
- The audit log is append-only; entries are never updated or deleted.
- Adding audit coverage for a new event type requires only a new `@EventListener` method in `AuditEventListener`.
- The `diff_json` column is nullable and can be populated incrementally — initially most entries will have `null` diffs.
- Query performance is ensured by targeted indexes; if the table grows very large, partitioning by `occurred_at` can be added later.
- The REST endpoints allow both per-entity history and org-wide activity feeds for dashboard or compliance use.
