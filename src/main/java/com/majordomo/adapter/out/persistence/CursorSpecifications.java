package com.majordomo.adapter.out.persistence;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared JPA Specifications for cursor-based queries.
 * Eliminates duplicate with/without cursor query methods.
 */
public final class CursorSpecifications {

    private CursorSpecifications() {
    }

    /**
     * Creates a specification for cursor-based pagination.
     *
     * @param cursor the exclusive start cursor (null for first page)
     * @param <T>    the entity type
     * @return a specification that filters by id greater than cursor
     */
    public static <T> Specification<T> afterCursor(UUID cursor) {
        return (root, query, cb) -> {
            if (cursor == null) {
                return cb.conjunction();
            }
            return cb.greaterThan(root.get("id"), cursor);
        };
    }

    /**
     * Creates a specification matching an entity field.
     *
     * @param field the field name
     * @param value the value to match
     * @param <T>   the entity type
     * @return a specification, or conjunction if value is null
     */
    public static <T> Specification<T> fieldEquals(String field, Object value) {
        return (root, query, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get(field), value);
        };
    }

    /**
     * Creates a case-insensitive LIKE specification across multiple fields.
     *
     * @param query  the search term
     * @param fields the fields to search
     * @param <T>    the entity type
     * @return a specification matching any field
     */
    public static <T> Specification<T> searchAcrossFields(String query, String... fields) {
        return (root, q, cb) -> {
            if (query == null || query.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + query.toLowerCase() + "%";
            List<Predicate> predicates = new ArrayList<>();
            for (String field : fields) {
                predicates.add(cb.like(cb.lower(root.get(field)), pattern));
            }
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
