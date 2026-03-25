package com.majordomo.application.identity;

import com.majordomo.domain.port.out.identity.CredentialRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

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
}
