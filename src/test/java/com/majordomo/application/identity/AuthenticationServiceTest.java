package com.majordomo.application.identity;

import com.majordomo.domain.model.identity.Credential;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.identity.CredentialRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthenticationService}.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CredentialRepository credentialRepository;

    private AuthenticationService authService;

    /** Sets up the service under test with mocked dependencies. */
    @BeforeEach
    void setUp() {
        authService = new AuthenticationService(userRepository, credentialRepository);
    }

    /** Verifies that an unknown username throws {@link UsernameNotFoundException}. */
    @Test
    void loadUserByUsernameThrowsWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class,
                () -> authService.loadUserByUsername("unknown"));
    }

    /** Verifies that a known user with a credential returns correct {@link UserDetails}. */
    @Test
    void loadUserByUsernameReturnsUserDetailsWhenFound() {
        UUID userId = UUID.randomUUID();
        var user = new User(userId, "robsartin", "rob@example.com");
        var credential = new Credential(UUID.randomUUID(), userId, "$2a$10$hashedpassword");

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(credential));

        UserDetails details = authService.loadUserByUsername("robsartin");
        assertEquals("robsartin", details.getUsername());
        assertEquals("$2a$10$hashedpassword", details.getPassword());
    }
}
