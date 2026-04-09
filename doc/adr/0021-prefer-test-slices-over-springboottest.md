# 21. Prefer test slices over @SpringBootTest

Date: 2026-04-09

## Status

Accepted

## Context

As the Majordomo codebase grows, a growing number of tests were annotated with `@SpringBootTest`. This annotation loads the entire application context — every bean, every configuration, every database connection — even when the test only needs a single controller, a repository, or a serialization check.

The consequences:

- **Slow feedback loops.** Full-context startup takes seconds per test class, discouraging developers from running tests locally.
- **Unfocused failures.** A test that loads everything can fail for reasons unrelated to what it is testing — a misconfigured bean in a completely unrelated service.
- **Excessive mocking.** When the full context loads, tests must mock or satisfy every dependency in the graph, even those irrelevant to the behavior under test.

Spring Boot provides test slices — purpose-built annotations that load only the layer needed — as the recommended alternative to `@SpringBootTest` for focused tests.

## Decision

We adopt the following test pyramid for Majordomo:

### Preferred test slices

| Slice | Annotation | What it loads | When to use |
|-------|------------|---------------|-------------|
| Controller layer | `@WebMvcTest(Controller.class)` | Web layer only: controllers, filters, `@ControllerAdvice`, security | HTTP behavior, request validation, response structure, access control |
| Persistence layer | `@DataJpaTest` | JPA layer only: entities, repositories, Flyway, embedded or Testcontainers DB | Custom queries, specifications, constraint enforcement |
| Serialization | `@JsonTest` | Jackson only | JSON serialization contracts, sensitive field exclusion, date formats |
| Real database fidelity | `@DataJpaTest` + Testcontainers | JPA layer with real PostgreSQL container | PostgreSQL-specific features, dialect-dependent queries |

### When `@SpringBootTest` is acceptable

- **End-to-end smoke tests**: A small number (ideally ≤ 3) of tests that verify the application context loads and critical paths work across all layers.
- **True integration tests**: Tests tagged with `@IntegrationTest` that exercise the full stack against Testcontainers-managed infrastructure.

### Patterns to follow

1. **Specify controllers explicitly.** Use `@WebMvcTest(MyController.class)`, not bare `@WebMvcTest`, to avoid loading all controllers and their transitive dependencies.
2. **Import security when testing access rules.** Use `@Import(SecurityConfig.class)` and mock its dependencies (`ApiKeyRepository`, `OAuth2UserService`) rather than loading the entire application.
3. **Mock only direct dependencies.** Use `@MockitoBean` for the beans the controller under test actually injects, nothing more.
4. **One test class per controller or concern.** Each `@WebMvcTest` class should focus on a single controller. Cross-cutting concerns (validation, exception handling, security rules) get their own dedicated test classes.

## Consequences

- **Faster tests.** Slice-based tests start in under 2 seconds versus 5+ seconds for `@SpringBootTest` contexts.
- **Focused failures.** A `@WebMvcTest` test can only fail for web-layer reasons, making diagnosis immediate.
- **More explicit dependencies.** Each test class declares exactly what it needs via `@MockitoBean` and `@Import`, making the dependency graph visible.
- **Slightly more boilerplate per test class.** Each `@WebMvcTest` must mock `ApiKeyRepository` and `OAuth2UserService` when importing `SecurityConfig`. This is acceptable because the boilerplate makes the test's scope explicit.
- **`@SpringBootTest` count should stay small.** New tests should default to the appropriate slice. PRs adding new `@SpringBootTest` tests should justify why a slice is insufficient.
