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

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Custom OAuth2 user service that links Google accounts to Majordomo users.
 * Creates a new user on first login or links to an existing user by email.
 */
@Service
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthLinkRepository oauthLinkRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;

    /**
     * Constructs the service with required repository dependencies.
     *
     * @param oauthLinkRepository    port for OAuth link persistence
     * @param userRepository         port for user persistence
     * @param organizationRepository port for organization persistence
     * @param membershipRepository   port for membership persistence
     */
    public OAuth2UserService(OAuthLinkRepository oauthLinkRepository,
                             UserRepository userRepository,
                             OrganizationRepository organizationRepository,
                             MembershipRepository membershipRepository) {
        this.oauthLinkRepository = oauthLinkRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(request);

        String provider = request.getClientRegistration().getRegistrationId();
        String externalId = oauth2User.getAttribute("sub");
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");

        var existingLink = oauthLinkRepository.findByProviderAndExternalId(provider, externalId);
        if (existingLink.isPresent()) {
            return oauth2User;
        }

        var existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            createOAuthLink(existingUser.get().getId(), provider, externalId, email);
        } else {
            var user = createNewUser(name, email);
            createOAuthLink(user.getId(), provider, externalId, email);
        }

        return oauth2User;
    }

    /**
     * Creates a new user along with a personal organization and owner membership.
     *
     * @param name  the display name from the OAuth provider
     * @param email the email address from the OAuth provider
     * @return the newly created user
     */
    User createNewUser(String name, String email) {
        Instant now = Instant.now();

        String username = email.split("@")[0] + "-" + UUID.randomUUID().toString().substring(0, 8);
        var user = new User(UUID.randomUUID(), username, email);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        var savedUser = userRepository.save(user);

        var org = new Organization(UUID.randomUUID(), name + "'s Organization");
        org.setCreatedAt(now);
        org.setUpdatedAt(now);
        var savedOrg = organizationRepository.save(org);

        var membership = new Membership(UUID.randomUUID(), savedUser.getId(),
                savedOrg.getId(), MemberRole.OWNER);
        membership.setCreatedAt(now);
        membership.setUpdatedAt(now);
        membershipRepository.save(membership);

        return savedUser;
    }

    /**
     * Persists a new OAuth link between a user and an external identity.
     *
     * @param userId     the Majordomo user ID
     * @param provider   the OAuth provider name
     * @param externalId the provider-specific user identifier
     * @param email      the email address from the OAuth provider
     */
    void createOAuthLink(UUID userId, String provider, String externalId, String email) {
        Instant now = Instant.now();
        var link = new OAuthLink(UUID.randomUUID(), userId, provider, externalId, email);
        link.setCreatedAt(now);
        link.setUpdatedAt(now);
        oauthLinkRepository.save(link);
    }
}
