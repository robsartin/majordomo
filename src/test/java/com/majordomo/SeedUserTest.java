package com.majordomo;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the BCrypt hash used in V2 migration matches the expected password.
 */
class SeedUserTest {

    /** The seeded password must match the BCrypt hash in V2__seed_default_user.sql. */
    @Test
    void seededPasswordMatchesBcryptHash() {
        var encoder = new BCryptPasswordEncoder();
        // The hash from V2__seed_default_user.sql
        String hash = "$2a$10$6t00FGN9bsAczx5/czYSnu2pHhqWycVfG5lNR2lURHGsH5RsdGX1q";
        assertTrue(encoder.matches("xyzzyPLAN9", hash));
    }
}
