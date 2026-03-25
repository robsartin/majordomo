package com.majordomo.adapter.in.web.identity;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a new user within an organization.
 *
 * @param username the desired username
 * @param email    the user's email address
 * @param password the plaintext password (will be hashed with Argon2id)
 */
public record CreateUserRequest(
    @NotBlank String username,
    @NotBlank String email,
    @NotBlank String password
) {}
