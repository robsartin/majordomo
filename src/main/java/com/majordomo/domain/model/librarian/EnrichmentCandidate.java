package com.majordomo.domain.model.librarian;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One proposed external match for a {@link Book}, held for review rather than
 * applied directly.
 *
 * <p>Candidates exist because transcription from spines is lossy: 24 of the 57
 * seed rows were read imperfectly or not at all, and those are exactly the rows
 * an external catalog will match confidently and wrongly. Persisting the match
 * with its {@code score} and the raw {@code payload} means a reviewer can see
 * what was proposed and why.
 *
 * <p>A decided candidate is kept rather than deleted — including a rejected one
 * — so a later reviewer can see what was already turned down instead of being
 * offered it again.
 *
 * @param id          this candidate's identifier
 * @param bookId      the book the match was proposed for
 * @param source      which catalog proposed it, e.g. {@code OPEN_LIBRARY}
 * @param externalId  the identifier within that catalog
 * @param score       the matcher's 0-1 similarity score
 * @param confidence  the bucket that score falls into, which drives auto-apply
 * @param payload     the raw fields returned by the source, persisted as JSONB
 * @param retrievedAt when the source was queried
 * @param reviewedAt  when a human decided, or {@code null} while pending
 * @param accepted    the decision, or {@code null} while pending
 */
public record EnrichmentCandidate(
    UUID id,
    UUID bookId,
    String source,
    String externalId,
    double score,
    Confidence confidence,
    Map<String, String> payload,
    Instant retrievedAt,
    Instant reviewedAt,
    Boolean accepted
) {

    /**
     * Whether this candidate is still awaiting a decision.
     *
     * @return true while no one has accepted or rejected it
     */
    public boolean isPending() {
        return reviewedAt == null;
    }

    /**
     * Records a reviewer's decision, leaving everything else intact.
     *
     * @param wasAccepted true to accept the match, false to reject it
     * @param at          when the decision was made
     * @return a decided copy of this candidate
     */
    public EnrichmentCandidate withDecision(boolean wasAccepted, Instant at) {
        return new EnrichmentCandidate(
                id, bookId, source, externalId, score, confidence, payload, retrievedAt,
                at, wasAccepted);
    }
}
