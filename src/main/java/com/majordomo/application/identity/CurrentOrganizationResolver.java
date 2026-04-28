package com.majordomo.application.identity;

import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the authenticated user's identity plus the {@code organizationId} of
 * the first organization they belong to. Centralizes the membership-resolution
 * policy that was duplicated across five controllers (dashboard + the envoy
 * Thymeleaf controllers).
 *
 * <p>Returning a present {@link Resolved} with {@code organizationId == null}
 * (rather than throwing) preserves the existing controller idiom of redirecting
 * home when the user has no memberships. Callers that want a hard auth check
 * should use {@link OrganizationAccessService#verifyAccess(UUID)} instead.</p>
 */
@Component
public class CurrentOrganizationResolver {

    private final UserRepository users;
    private final MembershipRepository memberships;

    /**
     * Resolved authentication context returned by {@link #resolve(UserDetails)}.
     *
     * @param user           the authenticated user
     * @param organizationId the user's first organization id, or {@code null}
     *                       if the user has no memberships
     */
    public record Resolved(User user, UUID organizationId) { }

    /**
     * Constructs the resolver.
     *
     * @param users       outbound port for user lookups
     * @param memberships outbound port for membership lookups
     */
    public CurrentOrganizationResolver(UserRepository users, MembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    /**
     * Resolves the authenticated principal to a user + first-org pairing.
     *
     * @param principal the Spring Security {@code UserDetails} for the request
     * @return the resolved user (always present) and their first {@code organizationId}
     *         (or {@code null} if they have no memberships)
     * @throws java.util.NoSuchElementException if the user is not found in the
     *         {@link UserRepository} (should not happen in practice — Spring
     *         Security has already authenticated them)
     */
    public Resolved resolve(UserDetails principal) {
        User user = users.findByUsername(principal.getUsername()).orElseThrow();
        var allMemberships = memberships.findByUserId(user.getId());
        if (allMemberships.isEmpty()) {
            return new Resolved(user, null);
        }
        return new Resolved(user, allMemberships.get(0).getOrganizationId());
    }
}
