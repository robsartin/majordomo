package com.majordomo.domain.model.librarian;

import com.majordomo.domain.model.UuidFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichmentCandidateTest {

    @Test
    void enrichmentCandidate_carriesScoreAndPayload() {
        var id = UuidFactory.newId();
        var bookId = UuidFactory.newId();
        var now = Instant.now();

        var candidate = new EnrichmentCandidate(
                id,
                bookId,
                "OPEN_LIBRARY",
                "OL7353617M",
                0.92,
                Confidence.HIGH,
                Map.of("publisher", "Oxford University Press", "year", "2018"),
                now, null, null);

        assertThat(candidate.id()).isEqualTo(id);
        assertThat(candidate.bookId()).isEqualTo(bookId);
        assertThat(candidate.source()).isEqualTo("OPEN_LIBRARY");
        assertThat(candidate.externalId()).isEqualTo("OL7353617M");
        assertThat(candidate.score()).isEqualTo(0.92);
        assertThat(candidate.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(candidate.payload()).containsEntry("publisher", "Oxford University Press");
        assertThat(candidate.retrievedAt()).isEqualTo(now);
    }

    @Test
    void enrichmentCandidate_isPendingUntilItIsDecided() {
        var c = new EnrichmentCandidate(
                UuidFactory.newId(), UuidFactory.newId(), "OPEN_LIBRARY", "OL1M",
                0.9, Confidence.HIGH, Map.of(), Instant.now(), null, null);

        assertThat(c.isPending()).isTrue();
    }

    @Test
    void enrichmentCandidate_recordsAnAcceptedDecision() {
        var now = Instant.now();
        var decided = new EnrichmentCandidate(
                UuidFactory.newId(), UuidFactory.newId(), "OPEN_LIBRARY", "OL1M",
                0.9, Confidence.HIGH, Map.of(), now, null, null)
                .withDecision(true, now);

        assertThat(decided.isPending()).isFalse();
        assertThat(decided.accepted()).isTrue();
        assertThat(decided.reviewedAt()).isEqualTo(now);
    }

    @Test
    void enrichmentCandidate_recordsARejectionWithoutLosingThePayload() {
        var now = Instant.now();
        var rejected = new EnrichmentCandidate(
                UuidFactory.newId(), UuidFactory.newId(), "OPEN_LIBRARY", "OL1M",
                0.4, Confidence.LOW, Map.of("publisher", "OUP"), now, null, null)
                .withDecision(false, now);

        assertThat(rejected.isPending()).isFalse();
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.payload()).containsEntry("publisher", "OUP");
    }
}
