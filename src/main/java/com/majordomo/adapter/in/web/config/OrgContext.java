package com.majordomo.adapter.in.web.config;

import com.majordomo.domain.model.identity.User;

import java.util.UUID;

/**
 * Authentication context for a Thymeleaf page handler — the resolved user
 * plus the {@link UUID} of their first organization. Always non-null on both
 * fields when injected by {@link OrgContextArgumentResolver}; if the user has
 * no membership the resolver throws {@link MissingOrganizationException}
 * before the handler runs.
 *
 * @param user           authenticated user
 * @param organizationId user's first organization id (non-null)
 */
public record OrgContext(User user, UUID organizationId) {
    /**
     * Convenience accessor matching {@code ctx.user().getUsername()}.
     *
     * @return the user's username
     */
    public String username() {
        return user.getUsername();
    }
}
