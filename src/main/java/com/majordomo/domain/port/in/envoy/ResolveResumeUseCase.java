package com.majordomo.domain.port.in.envoy;

import java.util.UUID;

/**
 * Inbound port for reading the user's résumé as grounding text (#349,
 * ADR-0028).
 */
public interface ResolveResumeUseCase {

    /**
     * Returns the text of the user's most recently uploaded résumé.
     *
     * @param userId the user whose résumé to read
     * @return the extracted text, never blank
     */
    String resolve(UUID userId);
}
