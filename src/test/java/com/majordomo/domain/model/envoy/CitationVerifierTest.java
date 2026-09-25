package com.majordomo.domain.model.envoy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The grounding check (#350, ADR-0028).
 *
 * <p>This is the mechanism that turns "the draft may not invent experience"
 * from an instruction in a prompt into something enforced. Nearly every test
 * here is a rejection, because a guard is only worth having if it is known to
 * fire.
 */
class CitationVerifierTest {

    private static final String RESUME = """
            Robert Sartin — Staff Software Engineer
            Acme Corp, 2019-2026. Java, Spring Boot, PostgreSQL.
            Led a team of 4 engineers on the payments platform.
            Delivered 12 projects across three business units.
            """;

    private static final String POSTING = """
            Staff Backend Engineer at Globex. Java/Spring, fully remote.
            Base salary 250000 USD.
            """;

    @Test
    void passes_whenEveryCitedSpanIsInTheResume() {
        GroundingCheck check = CitationVerifier.check(
                "I have led backend teams.",
                List.of(new ClaimCitation("Led a team", "Led a team of 4 engineers")),
                RESUME, POSTING);

        assertThat(check.passed()).isTrue();
        assertThat(check.problems()).isEmpty();
    }

    /** The control. Without this firing, nothing here is a guard. */
    @Test
    void fails_whenACitedSpanIsNotInTheResume() {
        GroundingCheck check = CitationVerifier.check(
                "I led engineering at Initech.",
                List.of(new ClaimCitation("Led engineering at Initech",
                        "Director of Engineering, Initech")),
                RESUME, POSTING);

        assertThat(check.passed()).isFalse();
        assertThat(check.problems()).singleElement().asString()
                .contains("Director of Engineering, Initech");
    }

    /**
     * PDF extraction puts line breaks and runs of spaces wherever the layout
     * had them. An exact match would reject citations that are genuinely
     * present, which would make the guard fire on honest drafts and get
     * switched off.
     */
    @Test
    void matchesAcrossTheWhitespaceAndCasePdfExtractionIntroduces() {
        GroundingCheck check = CitationVerifier.check(
                "I work in Java.",
                List.of(new ClaimCitation("Java experience", "java,   spring\n  boot")),
                RESUME, POSTING);

        assertThat(check.passed()).isTrue();
    }

    /**
     * ADR-0028 named this as the gap the citation check leaves open: a true
     * span, inflated. The résumé says a team of 4; the draft says 40 and cites
     * the span it came from, which exists. Checking the numbers closes it.
     */
    @Test
    void fails_whenTheDraftInflatesANumberFoundInTheSource() {
        GroundingCheck check = CitationVerifier.check(
                "I led a team of 40 engineers.",
                List.of(new ClaimCitation("Led a team", "Led a team of 4 engineers")),
                RESUME, POSTING);

        assertThat(check.passed()).isFalse();
        assertThat(check.problems()).singleElement().asString().contains("40");
    }

    /**
     * The defect #351 names for résumé bullets, and it is not specific to them.
     * A rewrite that swaps one of the résumé's numbers for another of the
     * résumé's numbers passes every check so far: the cited span is real, and
     * 12 does appear in the résumé — just not in the bullet being rewritten.
     * Checking each claim's numbers against its own cited span closes it.
     */
    @Test
    void fails_whenAClaimSwapsInANumberFromElsewhereInTheResume() {
        GroundingCheck check = CitationVerifier.check(
                "I led a team of 12 engineers.",
                List.of(new ClaimCitation(
                        "Led a team of 12 engineers", "Led a team of 4 engineers")),
                RESUME, POSTING);

        assertThat(check.passed()).isFalse();
        assertThat(check.problems()).singleElement().asString()
                .contains("12")
                .contains("Led a team of 4 engineers");
    }

    /** A claim restating its source's own numbers is exactly what should pass. */
    @Test
    void allowsAClaimThatKeepsTheNumbersInItsCitedSpan() {
        assertThat(CitationVerifier.check(
                "I led a team of 4 engineers.",
                List.of(new ClaimCitation(
                        "Led a team of 4 engineers", "Led a team of 4 engineers")),
                RESUME, POSTING).passed()).isTrue();
    }

    @Test
    void allowsNumbersThatAppearInTheResume() {
        assertThat(CitationVerifier.check(
                "At Acme from 2019 to 2026 I led 4 engineers.", List.of(), RESUME, POSTING)
                .passed()).isTrue();
    }

    /** The posting is a legitimate source too — its salary is not the résumé's. */
    @Test
    void allowsNumbersThatComeFromThePosting() {
        assertThat(CitationVerifier.check(
                "The 250000 base is in line with my expectations.", List.of(), RESUME, POSTING)
                .passed()).isTrue();
    }

    @Test
    void reportsEveryProblemAtOnceRatherThanTheFirst() {
        GroundingCheck check = CitationVerifier.check(
                "I led 40 people and 99 projects.",
                List.of(new ClaimCitation("Worked at Initech", "Senior Engineer at Initech")),
                RESUME, POSTING);

        assertThat(check.problems()).hasSize(3);
    }

    /** A draft making no factual claims and citing nothing is not a failure. */
    @Test
    void passes_whenThereIsNothingToCheck() {
        assertThat(CitationVerifier.check("I would love to talk.", List.of(), RESUME, POSTING)
                .passed()).isTrue();
    }
}
