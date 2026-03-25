package com.majordomo.adapter.in.web.identity;

/**
 * Request body for creating a new user within an organization.
 *
 * @param username the desired username
 * @param email    the user's email address
 * @param password the plaintext password (will be hashed with Argon2id)
 */
public record CreateUserRequest(
    String username,
    String email,
    String password
) {}
