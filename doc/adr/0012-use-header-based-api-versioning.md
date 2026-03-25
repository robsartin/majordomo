# 12. Use header-based API versioning

Date: 2026-03-24

## Status

Accepted

## Context

As Majordomo's REST APIs evolve, we need a versioning strategy that allows breaking changes without disrupting existing clients. Common approaches include URL path versioning (`/api/v1/...`), query parameter versioning, and header-based versioning.

URL path versioning pollutes the resource namespace — the version is not a resource, it is a contract. Query parameters are easily overlooked. Header-based versioning keeps URLs clean and resource-focused while making the version contract explicit.

## Decision

We will version REST APIs using a custom `X-API-Version` request header.

- The header value is a simple integer (e.g., `1`, `2`).
- When the header is absent, the API defaults to the latest version.
- Controllers use `headers = "X-API-Version=N"` on request mappings to route to the correct version.
- Multiple versions of the same endpoint can coexist in separate controller classes or methods.
- The current API version is returned in the `X-API-Version` response header.

Example:

```
GET /api/properties
X-API-Version: 1
```

## Consequences

- URLs remain stable and version-free — bookmarks, logs, and documentation stay clean.
- Versioning is opt-in for clients; omitting the header gets the latest version.
- Multiple API versions can run side-by-side without URL conflicts.
- Clients must be aware of the header convention — this is less discoverable than URL path versioning.
- API documentation (see ADR-0013) must clearly surface the versioning scheme.
