package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.MaterialBrief;
import com.majordomo.domain.model.envoy.MaterialKind;
import com.majordomo.domain.model.envoy.Tone;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The prompt for drafting (#350, ADR-0028).
 *
 * <p>The verifier rejects a draft that cites nothing or invents a number. If
 * the prompt does not say so, every honest draft fails the check and the
 * feature is unusable — the guard and the instructions have to describe the
 * same rules.
 */
class MaterialPromptBuilderTest {

    private final MaterialPromptBuilder builder = new MaterialPromptBuilder();

    @Test
    void systemPrompt_demandsACitationForEveryClaim() {
        String system = builder.build(brief(MaterialKind.COVER_LETTER, Tone.DIRECT)).systemPrompt();

        assertThat(system).containsIgnoringCase("cite");
        assertThat(system).containsIgnoringCase("résumé");
    }

    /**
     * The numeric rule exists because the verifier enforces it. A model told to
     * write "ten years of experience" from a résumé that only carries dates
     * would produce a draft rejected for a number it was never told to avoid.
     */
    @Test
    void systemPrompt_forbidsNumbersThatAreNotInTheSources() {
        String system = builder.build(brief(MaterialKind.COVER_LETTER, Tone.DIRECT)).systemPrompt();

        assertThat(system).containsIgnoringCase("number");
    }

    @Test
    void userPrompt_carriesTheResumeAndThePosting() {
        var prompt = builder.build(brief(MaterialKind.COVER_LETTER, Tone.DIRECT));

        assertThat(prompt.userPrompt()).contains("Led a team of 4 engineers");
        assertThat(prompt.userPrompt()).contains("Java, Spring, remote.");
        assertThat(prompt.userPrompt()).contains("Globex");
    }

    @Test
    void prompt_describesTheKindAndToneAsked() {
        var prompt = builder.build(brief(MaterialKind.INTRO_MESSAGE, Tone.WARM));

        assertThat(prompt.userPrompt() + prompt.systemPrompt())
                .containsIgnoringCase("intro")
                .containsIgnoringCase("warm");
    }

    /** Each kind has to ask for something different, or they are one feature. */
    @Test
    void everyKind_producesItsOwnInstructions() {
        assertThat(java.util.Arrays.stream(MaterialKind.values())
                .map(kind -> builder.build(brief(kind, Tone.DIRECT)).userPrompt())
                .toList())
                .doesNotHaveDuplicates();
    }

    private static MaterialBrief brief(MaterialKind kind, Tone tone) {
        JobPosting posting = new JobPosting();
        posting.setId(UUID.randomUUID());
        posting.setCompany("Globex");
        posting.setTitle("Staff Backend Engineer");
        posting.setRawText("Java, Spring, remote.");
        posting.setFetchedAt(Instant.now());
        return new MaterialBrief(posting, Optional.empty(), kind, tone,
                "Staff Engineer at Acme. Led a team of 4 engineers.");
    }
}
