# 14. Use Checkstyle for code style enforcement

Date: 2026-03-24

## Status

Accepted

## Context

With multiple domain packages and a hexagonal architecture, consistent code style reduces cognitive load when navigating the codebase. Style rules should be enforced automatically rather than relying on manual review, and violations should fail the build so they are caught before code reaches a PR.

## Decision

We will use Checkstyle integrated into the Maven build to enforce code style.

- Checkstyle runs during the `validate` phase via the `maven-checkstyle-plugin`.
- The configuration is based on Google's Java style with project-specific overrides stored in `config/checkstyle/checkstyle.xml`.
- Checkstyle violations fail the build — no warnings-only mode.
- The suppressions file `config/checkstyle/suppressions.xml` allows targeted exceptions where rules conflict with framework requirements.

## Consequences

- Code style is consistent across all packages and enforced automatically.
- Style issues are caught at build time, before code review.
- New contributors get immediate feedback on style without waiting for review comments.
- The Google base style is well-documented and widely understood.
- Some rules may need suppression for generated code or framework conventions (e.g., JPA entity boilerplate).
