# Authentication

## Overview

Majordomo uses Spring Security for authentication. The current implementation supports
form-based username/password login. OAuth2 support (Google, etc.) is planned for the future
(see ADR-0016).

## Architecture

Authentication follows the hexagonal architecture:

- **Inbound adapter**: `LoginController` serves the login page; Spring Security handles POST /login
- **Application service**: `AuthenticationService` implements Spring Security's `UserDetailsService`
- **Outbound ports**: `UserRepository.findByUsername()` and `CredentialRepository.findByUserId()`

Spring Security concerns stay in the adapter layer. The domain model knows nothing about
Spring Security.

## Password Hashing

Passwords are hashed with Argon2id via Spring Security's `Argon2PasswordEncoder`. Argon2id is
the Password Hashing Competition winner, resistant to GPU/ASIC attacks with configurable
memory cost.

## Login Flow

1. User visits `/login` (GET) — `LoginController` returns the Thymeleaf login form
2. User submits credentials (POST /login) — Spring Security intercepts
3. Spring Security calls `AuthenticationService.loadUserByUsername()`
4. `AuthenticationService` loads the `User` and `Credential` via domain ports
5. Spring Security verifies the password against the Argon2id hash
6. On success, redirects to `/`; on failure, redirects to `/login?error`

## Adding Users

Currently users are seeded via Flyway migrations. The default user:

- Username: `robsartin`
- Email: `rob.sartin@gmail.com`

To add a new user, create a Flyway migration that inserts into `users`, `credentials`,
`organizations`, and `memberships` tables. Use `Argon2PasswordEncoder` to generate the
password hash.

## Future: OAuth2

The system is designed to support OAuth2 providers (Google, GitHub, etc.) via Spring Security
OAuth2 Client. The `Credential` table separation from `User` allows multiple authentication
methods per user. When OAuth2 is added:

- A new `OAuthLink` entity or additional credential types will map external identities to users
- Existing form login will continue to work alongside OAuth2
- The `AuthenticationService` pattern extends naturally to the OAuth2 flow
