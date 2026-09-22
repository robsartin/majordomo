package com.majordomo.domain.model.envoy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants for generated application materials (#348, ADR-0028).
 */
class ApplicationMaterialTest {

    @Test
    void claimCitation_rejectsABlankClaim() {
        assertThatThrownBy(() -> new ClaimCitation("  ", "Staff Engineer at Acme Corp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void claimCitation_rejectsABlankSource() {
        assertThatThrownBy(() -> new ClaimCitation("Ten years of Java", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The whole guard is "every cited span appears in the résumé". A span of
     * {@code "a"} appears in every résumé ever written, so a citation short
     * enough to match anything would satisfy the check while evidencing
     * nothing. Rejecting it here makes the degenerate case unconstructible
     * rather than something the verifier has to remember to exclude.
     */
    @Test
    void claimCitation_rejectsASourceTooShortToBeEvidence() {
        assertThatThrownBy(() -> new ClaimCitation("Ten years of Java", "Java"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void claimCitation_acceptsAShortButSubstantivePhrase() {
        assertThat(new ClaimCitation("Knows Spring", "Java, Spring Boot").source())
                .isEqualTo("Java, Spring Boot");
    }

    @Test
    void material_rejectsBlankContent() {
        assertThatThrownBy(() -> material("   ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A material is a record of something that may already have been sent. If
     * the caller's list stayed live inside it, the record of what was claimed
     * could change after the fact, which is the one thing it exists to prevent.
     */
    @Test
    void material_doesNotShareTheCallersClaimList() {
        List<ClaimCitation> claims = new ArrayList<>();
        claims.add(new ClaimCitation("Knows Spring", "Java, Spring Boot"));

        ApplicationMaterial subject = material("Dear hiring manager", claims);
        claims.clear();

        assertThat(subject.claims()).hasSize(1);
    }

    @Test
    void material_claimsCannotBeMutatedThroughTheAccessor() {
        ApplicationMaterial subject = material("Dear hiring manager",
                List.of(new ClaimCitation("Knows Spring", "Java, Spring Boot")));

        assertThatThrownBy(() -> subject.claims().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static ApplicationMaterial material(String content, List<ClaimCitation> claims) {
        return new ApplicationMaterial(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Optional.empty(),
                MaterialKind.COVER_LETTER,
                Tone.DIRECT,
                content,
                claims,
                "claude-opus-5",
                Instant.now(),
                Optional.empty());
    }
}
