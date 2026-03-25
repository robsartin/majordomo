package com.majordomo.domain.port.in.identity;

import com.majordomo.domain.model.identity.User;

import java.util.UUID;

/**
 * Inbound port for user management operations.
 */
public interface ManageUserUseCase {

    /**
     * Creates a new user and adds them to the specified organization as a MEMBER.
     *
     * @param username       the desired username
     * @param email          the user's email
     * @param plainPassword  the plaintext password (will be hashed)
     * @param organizationId the organization to add the user to
     * @param callerUserId   the ID of the user performing the action
     * @return the created user
     * @throws IllegalArgumentException if username or email already exists
     * @throws org.springframework.security.access.AccessDeniedException if caller is not OWNER or ADMIN
     */
    User createUser(String username, String email, String plainPassword,
                    UUID organizationId, UUID callerUserId);
}
