# 13. Use SpringDoc OpenAPI for API documentation

Date: 2026-03-24

## Status

Accepted

## Context

Majordomo exposes REST APIs for each service (Concierge, Steward, Herald). Consumers need accurate, up-to-date documentation of available endpoints, request/response schemas, and the header-based versioning convention (see ADR-0012). Manually maintained documentation drifts from the code.

## Decision

We will use SpringDoc OpenAPI to generate API documentation directly from the codebase.

- SpringDoc scans Spring MVC controllers and generates an OpenAPI 3.x specification automatically.
- Swagger UI is served at `/swagger-ui.html` for interactive exploration and testing.
- The OpenAPI spec is available at `/v3/api-docs` in JSON format.
- APIs are grouped by service (Concierge, Steward, Herald) using SpringDoc group configuration.
- The `X-API-Version` header is documented as a global parameter across all endpoints.

## Consequences

- Documentation is always in sync with the code — no manual spec maintenance.
- Swagger UI provides an interactive tool for testing APIs during development.
- Developers can annotate controllers and models with OpenAPI annotations for richer documentation where auto-detection is insufficient.
- Adds a runtime dependency; the Swagger UI endpoint should be restricted or disabled in production if the API is not public-facing.
