# 16. Use Spring Security with form login, extensible to OAuth2

Date: 2026-03-25

## Status

Accepted

## Context

Majordomo needs authentication to protect API endpoints and provide user-specific views. The initial requirement is simple username/password login for a single user, but the system must accommodate OAuth2 providers (Google, etc.) in the future without rearchitecting.

Credentials are already separated from the User profile (see domain model), which supports multiple authentication methods per user.

## Decision

We will use Spring Security as the authentication framework.

Initial implementation:
- Form-based login at `/login` (GET returns page, POST authenticates)
- BCrypt for password hashing via Spring Security's `PasswordEncoder`
- A `UserDetailsService` implementation bridges Spring Security to the hexagonal domain ports (`UserRepository`, `CredentialRepository`)
- The root URL `/` is publicly accessible; `/api/**` requires authentication
- Swagger UI endpoints remain accessible for development

Future OAuth2:
- Spring Security OAuth2 Client will be added for Google and other providers
- The `UserDetailsService` pattern extends naturally — OAuth2 user info maps to the same `User` domain model
- Additional authentication methods will be linked to existing users via the `Credential` table or a new `OAuthLink` entity

## Consequences

- Spring Security is the industry standard for Java web security, with extensive documentation and community support.
- Form login is simple to implement and test.
- The `UserDetailsService` abstraction keeps Spring Security concerns in the adapter layer — the domain knows nothing about Spring Security.
- BCrypt is deliberately slow, protecting against brute-force attacks on the credential store.
- OAuth2 can be added later as an additional authentication method without changing the existing login flow.
- Session management adds server-side state; stateless JWT tokens may be considered for API-only clients in the future.
