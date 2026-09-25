package com.majordomo.domain.model.envoy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that a generated draft says nothing its sources do not support
 * (#350, ADR-0028).
 *
 * <p>Two checks, because they catch different lies.
 *
 * <p><strong>Cited spans</strong> must occur in the résumé. This catches an
 * employer, title or credential the model invented outright: a fabricated claim
 * has nothing real to point at.
 *
 * <p><strong>Numbers</strong> in the draft must occur in the résumé or the
 * posting. ADR-0028 named the hole the first check leaves — a true span,
 * inflated, where the résumé says a team of 4 and the draft says 40 while
 * citing the span it came from. The span exists, so citation checking passes it
 * and number checking does not.
 *
 * <p>A claim's own numbers are checked against its own cited span, not the
 * whole résumé. Swapping one résumé number for another passes the two checks
 * above — the span is real, the number does occur somewhere — while changing
 * what the cited line actually says.
 *
 * <p>None of these checks makes the draft true. Both make specific kinds of untrue
 * expensive, and what remains is why the citations are shown to a human.
 */
public final class CitationVerifier {

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private CitationVerifier() { }

    /**
     * Checks a draft against the material it was written from.
     *
     * @param draft      the generated text
     * @param claims     the claims the model reported, with their cited spans
     * @param resumeText the user's résumé — the only source for cited spans
     * @param postingText the job posting, an additional source for numbers
     * @return what was wrong, if anything
     */
    public static GroundingCheck check(
            String draft, List<ClaimCitation> claims, String resumeText, String postingText) {

        String resume = normalise(resumeText);
        List<String> problems = new ArrayList<>();

        for (ClaimCitation claim : claims) {
            if (!resume.contains(normalise(claim.source()))) {
                problems.add("Cited source is not in the résumé: \"" + claim.source()
                        + "\" (claim: " + claim.claim() + ")");
            }
            // Scoped to this claim's own span, not the whole résumé. Swapping
            // one of the résumé's numbers for another of the résumé's numbers
            // passes every other check here — the span is real and the number
            // does appear somewhere — while changing what the cited line says.
            // That is the rewrite defect #351 names, and it is not specific to
            // résumé bullets.
            Set<String> cited = numbersIn(claim.source());
            for (String number : numbersIn(claim.claim())) {
                if (!cited.contains(number)) {
                    problems.add("Claim states " + number + ", which is not in the span it "
                            + "cites: \"" + claim.source() + "\"");
                }
            }
        }

        Set<String> available = numbersIn(resumeText);
        available.addAll(numbersIn(postingText));
        for (String number : numbersIn(draft)) {
            if (!available.contains(number)) {
                problems.add("Draft states a number that is in neither the résumé nor "
                        + "the posting: " + number);
            }
        }

        return new GroundingCheck(problems);
    }

    /**
     * Collapses whitespace and case.
     *
     * <p>PDF extraction puts line breaks and runs of spaces wherever the page
     * layout had them, so an exact match would reject citations that really are
     * present. A guard that fires on honest drafts is a guard that gets turned
     * off. Nothing else is normalised: letters and digits are the claim.
     */
    private static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /** Every run of digits, so "250,000" contributes "250" and "000" on both sides. */
    private static Set<String> numbersIn(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null) {
            return found;
        }
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
