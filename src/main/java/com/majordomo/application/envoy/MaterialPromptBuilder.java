package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.CategoryScore;
import com.majordomo.domain.model.envoy.ClaimCitation;
import com.majordomo.domain.model.envoy.MaterialBrief;
import com.majordomo.domain.model.envoy.MaterialKind;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.Tone;

import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Renders the prompts for drafting application materials (#350, ADR-0028).
 *
 * <p>The instructions describe the same rules {@code CitationVerifier}
 * enforces. That is not belt and braces — a model not told to cite would have
 * every draft rejected, and a model not told to avoid ungrounded numbers would
 * write "ten years of experience" from a résumé carrying only dates and fail
 * for it. The guard decides; the prompt is how the model finds out what it is
 * being judged against.
 */
@Component
public class MaterialPromptBuilder {

    private static final String RULES = """
            You draft job-application material for one candidate, for one posting.

            Grounding rules, which are checked mechanically after you answer:

            1. Every factual claim you make about the candidate must come from \
            their résumé below. For each one, return the claim and the exact span \
            of résumé text it came from. A claim you cannot cite must not appear \
            in the draft at all.
            2. Quote the cited span verbatim from the résumé. It is matched \
            against the résumé text, ignoring only differences in whitespace and \
            letter case. A span shorter than %d characters is rejected as too \
            small to be evidence.
            3. Do not state any number — a count, a duration, a percentage, a \
            salary, a year — unless that number appears literally in the résumé \
            or the posting. Write "over a decade" rather than "11 years" if the \
            résumé gives only dates. Numbers are checked against both documents \
            and an ungrounded one fails the whole draft.
            4. Never inflate. Citing a real span while overstating what it says \
            is the failure these rules exist to make difficult; do not attempt \
            to satisfy them while doing it.

            Respond with JSON only, no prose around it:
            {"draft": "<the text>", "claims": [{"claim": "<assertion>", \
            "source": "<verbatim résumé span>"}]}
            """.formatted(ClaimCitation.MIN_SOURCE_CHARS);

    /**
     * Builds the prompts for one draft.
     *
     * @param brief the posting, rationale, résumé, kind and tone
     * @return the rendered prompts
     */
    public MaterialPrompt build(MaterialBrief brief) {
        String user = """
                Draft: %s
                Tone: %s — %s

                %s

                --- POSTING ---
                %s at %s
                %s

                %s
                --- RÉSUMÉ ---
                %s
                """.formatted(
                describe(brief.kind()),
                brief.tone().name().toLowerCase(java.util.Locale.ROOT),
                describe(brief.tone()),
                instructionFor(brief.kind()),
                nullToEmpty(brief.posting().getTitle()),
                nullToEmpty(brief.posting().getCompany()),
                nullToEmpty(brief.posting().getRawText()),
                rationale(brief),
                brief.resumeText());
        return new MaterialPrompt(RULES, user);
    }

    private static String describe(MaterialKind kind) {
        return switch (kind) {
            case COVER_LETTER -> "a cover letter";
            case INTRO_MESSAGE -> "a short intro message";
            case SCREENING_ANSWERS -> "answers to standard screening questions";
            case RESUME_BULLETS -> "tailored résumé bullets";
        };
    }

    private static String instructionFor(MaterialKind kind) {
        return switch (kind) {
            case COVER_LETTER -> """
                    Three or four short paragraphs. Open with why this specific role, \
                    not why you want a job. Close with a concrete next step.""";
            case INTRO_MESSAGE -> """
                    Under 120 words, for a LinkedIn message or an email to a hiring \
                    manager. No salutation boilerplate; say who you are, why this \
                    role, and what you would bring.""";
            case SCREENING_ANSWERS -> """
                    Answer each briefly and separately, labelled: why this company, \
                    what you are looking for in your next role, and availability. \
                    Do not invent a salary figure or a notice period.""";
            case RESUME_BULLETS -> """
                    Rewrite the candidate's existing bullets in the posting's \
                    language. Each rewritten bullet must cite the original bullet it \
                    came from. Never change a number, a title or a date while \
                    rewriting — reframing is the task, restating is not.""";
        };
    }

    private static String describe(Tone tone) {
        return switch (tone) {
            case DIRECT -> "plain and brief, no throat-clearing";
            case WARM -> "conversational and personable, written to a human being";
            case FORMAL -> "conventional business register";
        };
    }

    /**
     * The scoring rationale, when the posting has been scored. It explains why
     * this role suits the candidate in the posting's own terms, which is the
     * material a good opening paragraph is made of.
     */
    private static String rationale(MaterialBrief brief) {
        return brief.report()
                .map(ScoreReport::categoryScores)
                .map(scores -> scores.stream()
                        .map(CategoryScore::rationale)
                        .filter(text -> text != null && !text.isBlank())
                        .collect(Collectors.joining("\n- ", "--- WHY THIS ROLE SCORED WELL ---\n- ",
                                "\n")))
                .orElse("");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
