package com.majordomo.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Codec for JSONB persistence columns. Centralises the Jackson setup and the
 * try/catch translation of serialisation errors into {@link IllegalStateException}
 * that mappers across the persistence layer were previously duplicating.
 *
 * <p>This is a static-only utility because the {@link ObjectMapper} is thread-safe
 * after configuration and there is exactly one canonical configuration for the
 * codebase: {@link JavaTimeModule} for {@code Instant}/{@code LocalDate} round-trip
 * and {@link Jdk8Module} for {@code Optional}.</p>
 */
public final class JsonColumnCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new Jdk8Module())
            .build();

    private JsonColumnCodec() { }

    /**
     * Serialises a value to its JSON string form for storage in a JSONB column.
     *
     * @param value value to serialise (may be {@code null}, in which case
     *              {@code null} is returned)
     * @param label short label included in the error message — typically the
     *              entity type or column name being serialised
     * @return the JSON representation, or {@code null} if {@code value} was {@code null}
     * @throws IllegalStateException if Jackson fails to serialise
     */
    public static String encode(Object value, String label) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialise " + label, ex);
        }
    }

    /**
     * Deserialises a JSON string into a class.
     *
     * @param json  JSON payload (may be {@code null}, in which case {@code null} is returned)
     * @param type  target class
     * @param label short label included in the error message
     * @param <T>   target type
     * @return the deserialised value, or {@code null} if {@code json} was {@code null}
     * @throws IllegalStateException if Jackson fails to deserialise
     */
    public static <T> T decode(String json, Class<T> type, String label) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialise " + label, ex);
        }
    }

    /**
     * Deserialises a JSON string into a generic type via {@link TypeReference}.
     * Use for collection/map shapes where {@link #decode(String, Class, String)}
     * cannot infer the element types.
     *
     * @param json  JSON payload (may be {@code null}, in which case {@code null} is returned)
     * @param type  target type token
     * @param label short label included in the error message
     * @param <T>   target type
     * @return the deserialised value, or {@code null} if {@code json} was {@code null}
     * @throws IllegalStateException if Jackson fails to deserialise
     */
    public static <T> T decode(String json, TypeReference<T> type, String label) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialise " + label, ex);
        }
    }
}
