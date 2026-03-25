package com.majordomo.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/**
 * Factory for generating UUIDv7 identifiers.
 * All entity IDs should be created through this factory
 * to ensure time-ordered, globally unique identifiers.
 */
public final class UuidFactory {

    private UuidFactory() {
    }

    /**
     * Generates a new UUIDv7 identifier.
     *
     * @return a time-ordered UUID
     */
    public static UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
