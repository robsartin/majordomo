package com.majordomo.application.identity;

import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CurrentOrganizationResolver}.
 */
class CurrentOrganizationResolverTest {

    private final UserRepository users = mock(UserRepository.class);
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final CurrentOrganizationResolver resolver =
            new CurrentOrganizationResolver(users, memberships);

    /** Resolves user with a single membership to (user, that org's id). */
    @Test
    void singleMembershipReturnsThatOrg() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        User user = new User(userId, "robsartin", "rob@example.com");
        when(users.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(memberships.findByUserId(userId)).thenReturn(List.of(
                new Membership(UUID.randomUUID(), userId, orgId, MemberRole.OWNER)));

        var result = resolver.resolve(userDetails("robsartin"));

        assertThat(result.user()).isEqualTo(user);
        assertThat(result.organizationId()).isEqualTo(orgId);
    }

    /** Selects the first membership when the user belongs to multiple orgs. */
    @Test
    void multipleMembershipsReturnsFirst() {
        UUID userId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        User user = new User(userId, "user", "u@example.com");
        when(users.findByUsername("user")).thenReturn(Optional.of(user));
        when(memberships.findByUserId(userId)).thenReturn(List.of(
                new Membership(UUID.randomUUID(), userId, first, MemberRole.OWNER),
                new Membership(UUID.randomUUID(), userId, second, MemberRole.OWNER)));

        var result = resolver.resolve(userDetails("user"));

        assertThat(result.organizationId()).isEqualTo(first);
    }

    /** Returns user with null organizationId when the user has no memberships. */
    @Test
    void noMembershipsReturnsNullOrgId() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "lonely", "l@example.com");
        when(users.findByUsername("lonely")).thenReturn(Optional.of(user));
        when(memberships.findByUserId(userId)).thenReturn(List.of());

        var result = resolver.resolve(userDetails("lonely"));

        assertThat(result.user()).isEqualTo(user);
        assertThat(result.organizationId()).isNull();
    }

    /** Throws when the user is not found in the repository. */
    @Test
    void unknownUserThrows() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(userDetails("ghost")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    private static UserDetails userDetails(String username) {
        return org.springframework.security.core.userdetails.User
                .withUsername(username).password("x").authorities("USER").build();
    }
}
