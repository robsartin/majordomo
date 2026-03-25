package com.majordomo;

import com.majordomo.domain.port.out.identity.CredentialRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying the seed user can be loaded from real PostgreSQL
 * after Flyway migrations.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SeedUserIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Seed user robsartin should exist after migrations. */
    @Test
    void seedUserExists() {
        var user = userRepository.findByUsername("robsartin");
        assertTrue(user.isPresent(), "Seed user robsartin should exist");
    }

    /** Seed user password should match expected value. */
    @Test
    void seedUserPasswordMatches() {
        var user = userRepository.findByUsername("robsartin").orElseThrow();
        var credential = credentialRepository.findByUserId(user.getId()).orElseThrow();
        assertNotNull(credential.getHashedPassword());
        assertTrue(passwordEncoder.matches("xyzzyPLAN9", credential.getHashedPassword()),
                "Password should match xyzzyPLAN9");
    }
}
