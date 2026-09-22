package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.ApplicationMaterial;
import com.majordomo.domain.model.envoy.MaterialKind;
import com.majordomo.domain.model.envoy.Tone;

import java.util.UUID;

/**
 * Inbound port for drafting application materials (#290, ADR-0028).
 */
public interface GenerateApplicationMaterialUseCase {

    /**
     * Drafts and stores one material for a posting.
     *
     * @param postingId      the posting to write for
     * @param kind           what to draft
     * @param tone           the register
     * @param userId         whose résumé grounds it
     * @param organizationId the owning org
     * @return the stored draft
     */
    ApplicationMaterial generate(
            UUID postingId, MaterialKind kind, Tone tone, UUID userId, UUID organizationId);
}
