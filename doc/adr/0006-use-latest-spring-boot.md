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

## Amendment, 2026-09-19: upgraded to Spring Boot 4.1.1 (#312)

This amends rather than supersedes. The decision above — track the latest stable
release and upgrade promptly — is unchanged, and this upgrade is that decision
being carried out. What follows is the record of what the 3.5 → 4 move actually
cost, because almost none of it was where we expected.

**The framework majors were not the expensive part.** #312 flagged Spring
Security 7, Hibernate 7 and Flyway 12 as the risk. Between them they produced
exactly one change: Security 7 now issues a relative login redirect (`/login`)
where 6.x sent an absolute one, and a single test assertion had been pinning the
old form.

**Boot 4's own restructuring was the expensive part**, and it fails quietly:

- **Test slices moved into per-technology modules.** `@WebMvcTest` is now in
  `boot.webmvc.test.autoconfigure`, `@DataJpaTest` in
  `boot.data.jpa.test.autoconfigure`, needing `spring-boot-starter-webmvc-test`
  and `spring-boot-starter-data-jpa-test`. 50 test files.
- **`spring-boot-starter-security-test` is now required for MockMvc + Security.**
  Without it `@WithMockUser` requests are simply unauthenticated: every secured
  endpoint answers 302 and 211 tests fail with no hint as to why. This was by far
  the largest single cause.
- **A raw `flyway-core` dependency no longer migrates anything.** Boot 4 moved
  Flyway auto-configuration into `spring-boot-starter-flyway`. Without it the app
  starts, Flyway never runs, and the failure surfaces as Hibernate reporting the
  first table alphabetically as missing — `addresses` — which points nowhere near
  the actual cause.
- **`spring-boot-starter-aop` no longer exists**; it is `spring-boot-starter-aspectj`.
- **`@EnableCaching` on the application class breaks every `@WebMvcTest`**, since
  the Boot 4 web slice supplies no `CacheManager`. It now lives on `CacheConfig`,
  where it belonged.

**Jackson 3 arrived with the BOM.** `tools.jackson.*` replaces
`com.fasterxml.jackson.*` for databind and core; annotations keep their old
package. `java.time` and `Optional` are handled natively, so `JavaTimeModule` and
`Jdk8Module` are gone. Jackson 2 remains on the classpath as a transitive
dependency of `anthropic-java`, so the two coexist — our own code is on 3, and
Jackson 2 survives only as another library's private detail.

**The JUnit override from #118 is gone.** Boot 4.1.1 manages JUnit 6.0.3, so the
`junit-jupiter.version` property that forced 6.x onto the Boot 3.5 BOM has been
removed and the test stack is fully on-BOM again.

The general lesson for the next major: Boot 4 splits capabilities into modules,
and a missing module does not fail the build — it removes behaviour. Both of the
worst failures here were silent, and both pointed somewhere other than the cause.
