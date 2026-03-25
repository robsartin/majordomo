package com.majordomo.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link UuidFactory}.
 */
class UuidFactoryTest {

    /** Generated IDs should not be null. */
    @Test
    void newIdReturnsNonNull() {
        UUID id = UuidFactory.newId();
        assertNotNull(id);
    }

    /** Generated IDs should be unique. */
    @Test
    void newIdReturnsUniqueValues() {
        UUID id1 = UuidFactory.newId();
        UUID id2 = UuidFactory.newId();
        assertNotEquals(id1, id2);
    }

    /** Generated IDs should be version 7 (time-ordered). */
    @Test
    void newIdReturnsVersion7() {
        UUID id = UuidFactory.newId();
        assertEquals(7, id.version());
    }

    /** Sequential IDs should be monotonically increasing. */
    @Test
    void newIdIsMonotonicallyIncreasing() {
        UUID id1 = UuidFactory.newId();
        UUID id2 = UuidFactory.newId();
        assertTrue(id1.compareTo(id2) < 0, "Sequential UUIDv7 should be ordered");
    }
}
