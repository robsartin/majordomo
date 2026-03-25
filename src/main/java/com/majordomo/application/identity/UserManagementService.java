package com.majordomo.application.identity;

import com.majordomo.domain.model.event.UserCreated;
import com.majordomo.domain.model.identity.Credential;
import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.identity.ManageUserUseCase;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.identity.CredentialRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import com.majordomo.domain.model.UuidFactory;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service for user management. Only organization OWNER or ADMIN
 * can create new users.
 */
@Service
public class UserManagementService implements ManageUserUseCase {

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    /**
     * Constructs the service with required dependencies.
     *
     * @param userRepository       port for user persistence
     * @param credentialRepository port for credential persistence
     * @param membershipRepository port for membership persistence
     * @param passwordEncoder      encoder for hashing passwords
     * @param eventPublisher       port for publishing domain events
     */
    public UserManagementService(UserRepository userRepository,
                                  CredentialRepository credentialRepository,
                                  MembershipRepository membershipRepository,
                                  PasswordEncoder passwordEncoder,
                                  EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public User createUser(String username, String email, String plainPassword,
                           UUID organizationId, UUID callerUserId) {
        // Check caller is OWNER or ADMIN
        var callerMemberships = membershipRepository.findByUserId(callerUserId);
        boolean isAdminOrOwner = callerMemberships.stream()
                .filter(m -> m.getOrganizationId().equals(organizationId))
                .anyMatch(m -> m.getRole() == MemberRole.OWNER
                        || m.getRole() == MemberRole.ADMIN);

        if (!isAdminOrOwner) {
            throw new AccessDeniedException(
                    "Only OWNER or ADMIN can create users");
        }

        // Check uniqueness
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException(
                    "Username already exists: " + username);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException(
                    "Email already exists: " + email);
        }

        // Create user
        Instant now = Instant.now();
        var user = new User(UuidFactory.newId(), username, email);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        var savedUser = userRepository.save(user);

        // Create credential
        var credential = new Credential(UuidFactory.newId(), savedUser.getId(),
                passwordEncoder.encode(plainPassword));
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        credentialRepository.save(credential);

        // Create membership as MEMBER
        var membership = new Membership(UuidFactory.newId(), savedUser.getId(),
                organizationId, MemberRole.MEMBER);
        membership.setCreatedAt(now);
        membership.setUpdatedAt(now);
        membershipRepository.save(membership);

        eventPublisher.publish(new UserCreated(
                savedUser.getId(), organizationId,
                savedUser.getUsername(), now));

        return savedUser;
    }
}
