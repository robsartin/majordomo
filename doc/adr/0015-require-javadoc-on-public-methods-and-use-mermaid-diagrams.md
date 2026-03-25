# 15. Require Javadoc on public methods and use Mermaid diagrams

Date: 2026-03-24

## Status

Accepted

## Context

Majordomo uses hexagonal architecture with multiple domain packages (see ADR-0004). Public methods on domain models, ports, and adapters form the contracts between layers. Without documentation, these contracts are implicit and require reading implementations to understand intent. Architectural and flow diagrams further aid comprehension but drift when maintained in external tools.

## Decision

All public methods must have Javadoc comments. This is enforced by Checkstyle (see ADR-0014) via the `MissingJavadocMethod` check.

Javadoc requirements:

- All public methods must have a Javadoc comment describing their purpose.
- `@param`, `@return`, and `@throws` tags are required where applicable.
- Simple getters, setters, and JPA entity accessors are exempt via Checkstyle suppression.
- Javadoc should describe *what* and *why*, not *how* — implementation details belong in inline comments if needed at all.

Mermaid diagrams:

- Mermaid diagrams are used in Javadoc and Markdown documentation where they clarify architecture, data flow, or entity relationships.
- The domain model diagram is maintained in `doc/domain-model.md` (see existing Mermaid class diagram).
- Sequence diagrams, flowcharts, and component diagrams should be added to package-level `package-info.java` or `doc/` files when they aid understanding of cross-service interactions or complex workflows.

## Consequences

- Public API contracts are explicit and discoverable through IDE tooling and generated documentation.
- Checkstyle enforcement ensures documentation does not decay over time.
- Mermaid diagrams stay in sync with the code because they live alongside it in version control.
- Getters/setters are exempt to avoid boilerplate Javadoc that adds no value.
- Writing meaningful Javadoc requires discipline — template comments like "Gets the id" should be avoided in favor of domain-relevant descriptions.
