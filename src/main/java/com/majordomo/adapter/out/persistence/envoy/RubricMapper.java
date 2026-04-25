package com.majordomo.adapter.out.persistence.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.majordomo.domain.model.envoy.Rubric;

/**
 * Maps between the {@link Rubric} record and {@link RubricEntity}, serialising
 * the record to/from JSON for the {@code body} JSONB column.
 */
final class RubricMapper {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new Jdk8Module())
            .build();

    private RubricMapper() { }

    static RubricEntity toEntity(Rubric rubric) {
        var e = new RubricEntity();
        e.setId(rubric.id());
        e.setOrganizationId(rubric.organizationId().orElse(null));
        e.setName(rubric.name());
        e.setVersion(rubric.version());
        e.setEffectiveFrom(rubric.effectiveFrom());
        try {
            e.setBody(MAPPER.writeValueAsString(rubric));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialise Rubric", ex);
        }
        return e;
    }

    static Rubric toDomain(RubricEntity e) {
        try {
            return MAPPER.readValue(e.getBody(), Rubric.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialise Rubric " + e.getId(), ex);
        }
    }
}
