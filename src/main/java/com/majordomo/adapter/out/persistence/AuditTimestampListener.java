package com.majordomo.adapter.out.persistence;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.lang.reflect.Field;
import java.time.Instant;

/**
 * JPA entity listener that automatically sets {@code createdAt} and
 * {@code updatedAt} timestamps on persist and update operations.
 */
public class AuditTimestampListener {

    /**
     * Sets createdAt and updatedAt before initial persist.
     *
     * @param entity the entity being persisted
     */
    @PrePersist
    public void prePersist(Object entity) {
        Instant now = Instant.now();
        setFieldIfNull(entity, "createdAt", now);
        setField(entity, "updatedAt", now);
    }

    /**
     * Sets updatedAt before update.
     *
     * @param entity the entity being updated
     */
    @PreUpdate
    public void preUpdate(Object entity) {
        setField(entity, "updatedAt", Instant.now());
    }

    private void setFieldIfNull(Object entity, String fieldName, Instant value) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                if (field.get(entity) == null) {
                    field.set(entity, value);
                }
            }
        } catch (IllegalAccessException e) {
            // Field not accessible, skip
        }
    }

    private void setField(Object entity, String fieldName, Instant value) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(entity, value);
            }
        } catch (IllegalAccessException e) {
            // Field not accessible, skip
        }
    }

    private Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
