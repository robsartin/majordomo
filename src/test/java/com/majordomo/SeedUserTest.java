package com.majordomo;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Argon2id hash used in V3 migration matches the expected password.
 */
class SeedUserTest {

    /** The seeded password must match the Argon2id hash in V3 migration. */
    @Test
    void seededPasswordMatchesArgon2idHash() {
        var encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        // The hash from V3__update_seed_password_argon2id.sql
        String hash = "$argon2id$v=19$m=16384,t=2,p=1"
                + "$7ZDV8a9vfQ5iasgoHzUA7g"
                + "$PUcR8b7h3I9Ti4sf+8tAfSxvr4+XLwyGF9dmso1eui0";
        assertTrue(encoder.matches("xyzzyPLAN9", hash));
    }
}
