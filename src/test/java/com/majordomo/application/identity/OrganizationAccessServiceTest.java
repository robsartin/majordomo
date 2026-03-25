package com.majordomo.application.identity;

import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrganizationAccessService}.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationAccessServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private OrganizationAccessService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final String username = "testuser";

    /**
     * Sets up the service and a default security context before each test.
     */
    @BeforeEach
    void setUp() {
        service = new OrganizationAccessService(userRepository, membershipRepository);
    }

    /**
     * Clears the security context after each test.
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Verifies that access is granted when the authenticated user is a member of the organization.
     */
    @Test
    void verifyAccessMemberOfOrgSucceeds() {
        setAuthentication(username);

        var user = new User(userId, username, "test@example.com");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        var membership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.MEMBER);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(membership));

        assertDoesNotThrow(() -> service.verifyAccess(orgId));
    }

    /**
     * Verifies that access is denied when the authenticated user is not a member of the organization.
     */
    @Test
    void verifyAccessNotMemberThrowsAccessDenied() {
        setAuthentication(username);

        var user = new User(userId, username, "test@example.com");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        var otherOrgId = UUID.randomUUID();
        var membership = new Membership(UUID.randomUUID(), userId, otherOrgId, MemberRole.MEMBER);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(membership));

        var ex = assertThrows(AccessDeniedException.class, () -> service.verifyAccess(orgId));
        assertEquals("Access denied to organization: " + orgId, ex.getMessage());
    }

    /**
     * Verifies that an exception is thrown when no authentication is present.
     */
    @Test
    void getAuthenticatedUserIdNoAuthThrowsAccessDenied() {
        SecurityContextHolder.clearContext();

        var ex = assertThrows(AccessDeniedException.class,
                () -> service.getAuthenticatedUserId());
        assertEquals("Not authenticated", ex.getMessage());
    }

    private void setAuthentication(String name) {
        var auth = new UsernamePasswordAuthenticationToken(name, "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
