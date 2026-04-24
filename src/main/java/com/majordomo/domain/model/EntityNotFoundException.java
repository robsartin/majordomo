package com.majordomo.domain.model;

import java.util.UUID;

/**
 * Thrown when a requested domain entity cannot be found.
 */
public class EntityNotFoundException extends RuntimeException {

    /**
     * Constructs the exception with entity type and ID.
     *
     * @param entityType the type of entity that was not found
     * @param id         the ID that was looked up
     */
    public EntityNotFoundException(String entityType, UUID id) {
        super(entityType + " not found: " + id);
    }

    /**
     * Constructs the exception with entity type and a natural key.
     *
     * @param entityType the type of entity that was not found
     * @param key        the key that was looked up (e.g. a name)
     */
    public EntityNotFoundException(String entityType, String key) {
        super(entityType + " not found: " + key);
    }
}
