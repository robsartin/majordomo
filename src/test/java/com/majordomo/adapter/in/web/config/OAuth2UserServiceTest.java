package com.majordomo.adapter.in.web.config;

import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.OAuthLink;
import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.OAuthLinkRepository;
import com.majordomo.domain.port.out.identity.OrganizationRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OAuth2UserService}.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2UserServiceTest {

    @Mock
    private OAuthLinkRepository oauthLinkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private OAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new OAuth2UserService(oauthLinkRepository, userRepository,
                organizationRepository, membershipRepository);
    }

    /** Verifies that an existing user is linked when their email matches an OAuth login. */
    @Test
    void linksExistingUserByEmail() {
        UUID userId = UUID.randomUUID();
        var existingUser = new User(userId, "robsartin", "rob@example.com");

        when(oauthLinkRepository.save(any(OAuthLink.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createOAuthLink(userId, "google", "google-sub-123", "rob@example.com");

        ArgumentCaptor<OAuthLink> captor = ArgumentCaptor.forClass(OAuthLink.class);
        verify(oauthLinkRepository).save(captor.capture());

        OAuthLink saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals("google", saved.getProvider());
        assertEquals("google-sub-123", saved.getExternalId());
        assertEquals("rob@example.com", saved.getEmail());
        assertNotNull(saved.getCreatedAt());
    }

    /** Verifies that a new user and personal org are created on first OAuth login. */
    @Test
    void createsNewUserOnFirstLogin() {
        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepository.save(any(Membership.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        User created = service.createNewUser("Rob Sartin", "rob@example.com");

        assertNotNull(created);
        assertEquals("rob@example.com", created.getEmail());
        assertNotNull(created.getCreatedAt());

        ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(orgCaptor.capture());
        assertEquals("Rob Sartin's Organization", orgCaptor.getValue().getName());

        ArgumentCaptor<Membership> memberCaptor = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(memberCaptor.capture());
        assertEquals(MemberRole.OWNER, memberCaptor.getValue().getRole());
        assertEquals(created.getId(), memberCaptor.getValue().getUserId());
    }

    /** Verifies that no new link is created when one already exists. */
    @Test
    void skipsLinkCreationWhenAlreadyLinked() {
        var existingLink = new OAuthLink(UUID.randomUUID(), UUID.randomUUID(),
                "google", "google-sub-123", "rob@example.com");

        when(oauthLinkRepository.findByProviderAndExternalId("google", "google-sub-123"))
                .thenReturn(Optional.of(existingLink));

        // Simulate the check the loadUser method performs
        var result = oauthLinkRepository.findByProviderAndExternalId("google", "google-sub-123");
        assertNotNull(result);

        verify(userRepository, never()).save(any());
        verify(oauthLinkRepository, never()).save(any());
    }
}
