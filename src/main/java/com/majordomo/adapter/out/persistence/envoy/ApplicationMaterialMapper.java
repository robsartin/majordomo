package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.adapter.out.persistence.JsonColumnCodec;
import com.majordomo.domain.model.envoy.ApplicationMaterial;

/**
 * Maps {@link ApplicationMaterial} to and from {@link ApplicationMaterialEntity},
 * serialising the record into the JSONB {@code body}.
 *
 * <p>Usage is mirrored into scalar columns so cost can be aggregated without
 * parsing JSONB, exactly as {@link ScoreReportMapper} does. A row with only
 * some of the three is read as having no usage rather than a half-formed one.
 */
final class ApplicationMaterialMapper {

    private ApplicationMaterialMapper() { }

    static ApplicationMaterialEntity toEntity(ApplicationMaterial material) {
        var e = new ApplicationMaterialEntity();
        e.setId(material.id());
        e.setOrganizationId(material.organizationId());
        e.setPostingId(material.postingId());
        e.setScoreReportId(material.scoreReportId().orElse(null));
        e.setKind(material.kind().name());
        e.setTone(material.tone().name());
        e.setLlmModel(material.llmModel());
        e.setGeneratedAt(material.generatedAt());
        material.usage().ifPresentOrElse(
                u -> {
                    e.setInputTokens(u.inputTokens());
                    e.setOutputTokens(u.outputTokens());
                    e.setLatencyMs(u.latencyMs());
                },
                () -> {
                    e.setInputTokens(null);
                    e.setOutputTokens(null);
                    e.setLatencyMs(null);
                });
        e.setBody(JsonColumnCodec.encode(material, "application material " + material.id()));
        return e;
    }

    static ApplicationMaterial toDomain(ApplicationMaterialEntity entity) {
        ApplicationMaterial body = JsonColumnCodec.decode(
                entity.getBody(), ApplicationMaterial.class,
                "application material " + entity.getId());
        // The scalar columns are derived state; the body is the source of
        // truth, so it is returned as stored rather than reassembled from
        // columns that could have been touched independently.
        return body;
    }
}
