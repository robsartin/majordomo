package com.majordomo.domain.port.out.identity;

import com.majordomo.domain.model.identity.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and locating users.
 * Users are the authenticated principals of the system; lookup by username and
 * email supports both login flows and de-duplication during registration.
 */
public interface UserRepository {

    /**
     * Persists a user, inserting or updating as needed.
     *
     * @param user the user to save
     * @return the saved user, including any generated or updated fields
     */
    User save(User user);

    /**
     * Retrieves a user by their unique identifier.
     *
     * @param id the user ID
     * @return the user, or empty if not found
     */
    Optional<User> findById(UUID id);

    /**
     * Retrieves a user by their username.
     *
     * @param username the username to search for
     * @return the matching user, or empty if no user has that username
     */
    Optional<User> findByUsername(String username);

    /**
     * Retrieves a user by their email address.
     *
     * @param email the email address to search for
     * @return the matching user, or empty if no user has that email
     */
    Optional<User> findByEmail(String email);
}
