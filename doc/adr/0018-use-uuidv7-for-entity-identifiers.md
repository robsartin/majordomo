# 18. Use UUIDv7 for all entity identifiers

Date: 2026-03-25

## Status
Accepted

## Context
Entity IDs need to be unique, globally uncoordinated, and time-sortable for efficient cursor-based pagination and database indexing. UUID v4 (random) provides uniqueness but is not time-ordered, leading to index fragmentation and making cursor pagination unreliable.

## Decision
We will use UUIDv7 (RFC 9562) for all entity primary keys, generated via `UuidFactory.newId()`.

UUIDv7 encodes a Unix timestamp in the most significant bits, making IDs monotonically increasing within a millisecond. This provides:
- Efficient B-tree indexing (sequential inserts)
- Reliable cursor-based pagination (WHERE id > cursor ORDER BY id)
- Embedded creation timestamp (extractable if needed)

Implementation uses the `uuid-creator` library via a domain-layer factory class.

## Consequences
- All new entities get time-ordered IDs automatically
- Cursor-based pagination is stable and efficient
- Existing v4 UUIDs from seed migrations are acceptable (they predate this decision)
- Single factory class makes the ID strategy swappable if needed
