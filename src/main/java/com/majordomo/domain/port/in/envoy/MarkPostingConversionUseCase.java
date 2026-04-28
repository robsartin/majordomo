package com.majordomo.domain.port.in.envoy;

import java.util.UUID;

/**
 * Inbound port for marking a job posting's APPLY_NOW conversion outcome.
 * Either path is terminal in v1 — toggling back to "open" is not modelled.
 */
public interface MarkPostingConversionUseCase {

    /**
     * Marks the posting as applied, setting {@code appliedAt} to "now" and
     * publishing a {@code PostingMarkedApplied} event. No-op if the posting
     * is already marked applied.
     *
     * @param postingId      the posting to mark
     * @param organizationId owning org
     * @throws IllegalArgumentException if no posting matches within the org
     */
    void markApplied(UUID postingId, UUID organizationId);

    /**
     * Marks the posting as dismissed (not interested), setting
     * {@code dismissedAt} to "now" and publishing a {@code PostingDismissed}
     * event. No-op if the posting is already dismissed.
     *
     * @param postingId      the posting to mark
     * @param organizationId owning org
     * @throws IllegalArgumentException if no posting matches within the org
     */
    void dismiss(UUID postingId, UUID organizationId);
}
