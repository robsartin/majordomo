package com.majordomo.application.identity;

import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Verifies the authenticated user has membership in the requested organization.
 * Used by controllers to enforce organization-level access control.
 */
@Service
public class OrganizationAccessService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    /**
     * Constructs the service with required repositories.
     *
     * @param userRepository       repository for user lookup
     * @param membershipRepository repository for membership lookup
     */
    public OrganizationAccessService(UserRepository userRepository,
                                      MembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Returns the authenticated user's ID by looking up the current security context username.
     *
     * @return the user's UUID
     * @throws AccessDeniedException if the user cannot be resolved
     */
    public UUID getAuthenticatedUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("User not found"))
                .getId();
    }

    /**
     * Verifies the authenticated user is a member of the specified organization.
     *
     * @param organizationId the organization to check
     * @throws AccessDeniedException if the user is not a member
     */
    public void verifyAccess(UUID organizationId) {
        UUID userId = getAuthenticatedUserId();
        boolean isMember = membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getOrganizationId().equals(organizationId));
        if (!isMember) {
            throw new AccessDeniedException(
                    "Access denied to organization: " + organizationId);
        }
    }
}
