package com.majordomo.application.identity;

import com.majordomo.domain.model.identity.Credential;
import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.identity.CredentialRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserManagementService}.
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EventPublisher eventPublisher;

    private UserManagementService service;

    private final UUID callerUserId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();

    /** Sets up the service under test with mocked dependencies. */
    @BeforeEach
    void setUp() {
        service = new UserManagementService(
                userRepository, credentialRepository,
                membershipRepository, passwordEncoder, eventPublisher);
    }

    /** Verifies that an ADMIN caller can create a user, credential, and membership. */
    @Test
    void createUserAsAdminCreatesUserCredentialMembership() {
        var adminMembership = new Membership(
                UUID.randomUUID(), callerUserId, organizationId, MemberRole.ADMIN);
        when(membershipRepository.findByUserId(callerUserId))
                .thenReturn(List.of(adminMembership));
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed");
        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        User result = service.createUser(
                "newuser", "new@example.com", "plaintext",
                organizationId, callerUserId);

        assertEquals("newuser", result.getUsername());
        assertEquals("new@example.com", result.getEmail());
        verify(userRepository).save(any(User.class));
        verify(credentialRepository).save(any(Credential.class));
        verify(membershipRepository).save(any(Membership.class));
    }

    /** Verifies that a MEMBER caller is denied user creation. */
    @Test
    void createUserAsMemberThrowsAccessDenied() {
        var memberMembership = new Membership(
                UUID.randomUUID(), callerUserId, organizationId, MemberRole.MEMBER);
        when(membershipRepository.findByUserId(callerUserId))
                .thenReturn(List.of(memberMembership));

        assertThrows(AccessDeniedException.class, () ->
                service.createUser("newuser", "new@example.com", "plaintext",
                        organizationId, callerUserId));

        verify(userRepository, never()).save(any());
    }

    /** Verifies that a duplicate username causes an {@link IllegalArgumentException}. */
    @Test
    void createUserDuplicateUsernameThrowsIllegalArgument() {
        var adminMembership = new Membership(
                UUID.randomUUID(), callerUserId, organizationId, MemberRole.ADMIN);
        when(membershipRepository.findByUserId(callerUserId))
                .thenReturn(List.of(adminMembership));
        when(userRepository.findByUsername("existing"))
                .thenReturn(Optional.of(new User()));

        assertThrows(IllegalArgumentException.class, () ->
                service.createUser("existing", "new@example.com", "plaintext",
                        organizationId, callerUserId));

        verify(userRepository, never()).save(any());
    }

    /** Verifies that a duplicate email causes an {@link IllegalArgumentException}. */
    @Test
    void createUserDuplicateEmailThrowsIllegalArgument() {
        var adminMembership = new Membership(
                UUID.randomUUID(), callerUserId, organizationId, MemberRole.ADMIN);
        when(membershipRepository.findByUserId(callerUserId))
                .thenReturn(List.of(adminMembership));
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("existing@example.com"))
                .thenReturn(Optional.of(new User()));

        assertThrows(IllegalArgumentException.class, () ->
                service.createUser("newuser", "existing@example.com", "plaintext",
                        organizationId, callerUserId));

        verify(userRepository, never()).save(any());
    }

    /** Verifies that the plaintext password is hashed via the encoder. */
    @Test
    void createUserHashesPassword() {
        var adminMembership = new Membership(
                UUID.randomUUID(), callerUserId, organizationId, MemberRole.ADMIN);
        when(membershipRepository.findByUserId(callerUserId))
                .thenReturn(List.of(adminMembership));
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("mySecretPass")).thenReturn("argon2hashed");
        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createUser("newuser", "new@example.com", "mySecretPass",
                organizationId, callerUserId);

        verify(passwordEncoder).encode(eq("mySecretPass"));
        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());
        assertEquals("argon2hashed", captor.getValue().getHashedPassword());
    }
}
