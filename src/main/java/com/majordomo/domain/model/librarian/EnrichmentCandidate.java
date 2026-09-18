package com.majordomo.domain.model.librarian;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One proposed external match for a {@link Book}, held for review rather than
 * applied directly.
 *
 * <p>Candidates exist because transcription from spines is lossy: roughly half
 * the seed shelf carried some caveat, and those rows are exactly the ones an
 * external catalog will match confidently and wrongly. Persisting the match
 * with its {@code score} and the raw {@code payload} means a reviewer can see
 * what was proposed and why, and a rejected match leaves a trail.
 *
 * @param id          this candidate's identifier
 * @param bookId      the book the match was proposed for
 * @param source      which catalog proposed it, e.g. {@code OPEN_LIBRARY}
 * @param externalId  the identifier within that catalog
 * @param score       the matcher's 0-1 similarity score
 * @param confidence  the bucket that score falls into, which drives auto-apply
 * @param payload     the raw fields returned by the source, persisted as JSONB
 * @param retrievedAt when the source was queried
 */
public record EnrichmentCandidate(
    UUID id,
    UUID bookId,
    String source,
    String externalId,
    double score,
    Confidence confidence,
    Map<String, String> payload,
    Instant retrievedAt
) { }
