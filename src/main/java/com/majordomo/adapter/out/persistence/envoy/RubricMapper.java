package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.adapter.out.persistence.JsonColumnCodec;
import com.majordomo.domain.model.envoy.Rubric;

/**
 * Maps between the {@link Rubric} record and {@link RubricEntity}, serialising
 * the record to/from JSON for the {@code body} JSONB column via
 * {@link JsonColumnCodec}.
 */
final class RubricMapper {

    private RubricMapper() { }

    static RubricEntity toEntity(Rubric rubric) {
        var e = new RubricEntity();
        e.setId(rubric.id());
        e.setOrganizationId(rubric.organizationId().orElse(null));
        e.setName(rubric.name());
        e.setVersion(rubric.version());
        e.setEffectiveFrom(rubric.effectiveFrom());
        e.setBody(JsonColumnCodec.encode(rubric, "Rubric"));
        return e;
    }

    static Rubric toDomain(RubricEntity e) {
        return JsonColumnCodec.decode(e.getBody(), Rubric.class, "Rubric " + e.getId());
    }
}
