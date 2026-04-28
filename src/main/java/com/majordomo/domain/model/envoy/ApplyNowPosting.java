package com.majordomo.domain.model.envoy;

import java.util.UUID;

/**
 * Read-model projection of an APPLY_NOW score report enriched with posting metadata,
 * for surfaces (e.g. dashboard) that need a flat row without re-joining the posting.
 *
 * @param reportId   the underlying {@link ScoreReport} id (target of any link to detail)
 * @param postingId  the {@link JobPosting} id
 * @param company    posting company (may be {@code null} if the posting omitted it)
 * @param title      posting title (may be {@code null})
 * @param location   posting location (may be {@code null})
 * @param finalScore the report's final score
 */
public record ApplyNowPosting(
        UUID reportId,
        UUID postingId,
        String company,
        String title,
        String location,
        int finalScore) { }
