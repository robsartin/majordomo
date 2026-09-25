package com.majordomo.adapter.in.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Computed WCAG AA contrast check for server-rendered pages (#320, #352).
 *
 * <p>{@code doc/accessibility.md} verifies contrast by a manual spot-check. That
 * catches what someone remembers to look at; this computes the ratio for every
 * text colour the templates actually use, so a washed-out utility class fails
 * the build instead of shipping.
 *
 * <p>Each text colour is measured against {@code gray-50} — the lightest surface
 * these pages put text on, and therefore the worst case. An unrecognised colour
 * token fails rather than being skipped: a check that silently ignores what it
 * does not know is not a check.
 */
class TemplateContrastTest {

    private static final double AA_NORMAL_TEXT = 4.5;

    private static final List<Path> PAGES = List.of(
            Path.of("src/main/resources/templates/librarian.html"),
            Path.of("src/main/resources/templates/librarian-book.html"),
            Path.of("src/main/resources/templates/librarian-review.html"),
            Path.of("src/main/resources/templates/envoy-report.html"));

    /** The Tailwind tokens these templates use, and their hex values. */
    private static final Map<String, String> PALETTE = Map.ofEntries(
            Map.entry("white", "#ffffff"),
            Map.entry("gray-50", "#f9fafb"),
            Map.entry("gray-200", "#e5e7eb"),
            Map.entry("gray-400", "#9ca3af"),
            Map.entry("gray-700", "#374151"),
            Map.entry("gray-900", "#111827"),
            Map.entry("indigo-600", "#4f46e5"),
            Map.entry("indigo-700", "#4338ca"),
            Map.entry("indigo-800", "#3730a3"),
            Map.entry("indigo-900", "#312e81"),
            Map.entry("green-50", "#f0fdf4"),
            Map.entry("green-700", "#15803d"),
            Map.entry("green-900", "#14532d"),
            Map.entry("red-50", "#fef2f2"),
            Map.entry("red-700", "#b91c1c"),
            Map.entry("red-900", "#7f1d1d"),
            Map.entry("gray-500", "#6b7280"),
            Map.entry("gray-600", "#4b5563"),
            Map.entry("gray-800", "#1f2937"),
            Map.entry("amber-800", "#92400e"),
            Map.entry("amber-900", "#78350f"),
            Map.entry("emerald-800", "#065f46"),
            Map.entry("red-800", "#991b1b"),
            Map.entry("emerald-600", "#059669"),
            Map.entry("emerald-700", "#047857"),
            Map.entry("amber-100", "#fef3c7"),
            Map.entry("emerald-100", "#d1fae5"),
            Map.entry("red-100", "#fee2e2"),
            Map.entry("red-200", "#fecaca"),
            Map.entry("gray-100", "#f3f4f6"),
            Map.entry("gray-300", "#d1d5db"));

    private static final String LIGHTEST_SURFACE = PALETTE.get("gray-50");

    private static final Pattern TEXT_CLASS = Pattern.compile("\\btext-([a-z]+(?:-\\d{2,3})?)\\b");

    /** Tailwind text utilities that set size or alignment rather than colour. */
    private static final Set<String> NOT_COLOURS =
            Set.of("sm", "base", "lg", "xl", "2xl", "3xl", "xs", "left", "right", "center");

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<String> textColoursIn(Path page) {
        var found = new LinkedHashSet<String>();
        Matcher m = TEXT_CLASS.matcher(read(page));
        while (m.find()) {
            String token = m.group(1);
            if (!NOT_COLOURS.contains(token)) {
                found.add(token);
            }
        }
        return found;
    }

    private static double relativeLuminance(String hex) {
        double[] srgb = new double[3];
        for (int i = 0; i < 3; i++) {
            int channel = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16);
            double c = channel / 255.0;
            srgb[i] = c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * srgb[0] + 0.7152 * srgb[1] + 0.0722 * srgb[2];
    }

    static double contrastRatio(String foregroundHex, String backgroundHex) {
        double a = relativeLuminance(foregroundHex);
        double b = relativeLuminance(backgroundHex);
        double lighter = Math.max(a, b);
        double darker = Math.min(a, b);
        return (lighter + 0.05) / (darker + 0.05);
    }

    @Test
    void contrastFormulaMatchesKnownReferenceValues() {
        // Positive control: the formula itself must be right before its verdicts mean anything.
        assertThat(contrastRatio("#000000", "#ffffff")).isEqualTo(21.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(contrastRatio("#ffffff", "#ffffff")).isEqualTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void everyTextColourIsARecognisedPaletteToken() {
        for (Path page : PAGES) {
            for (String token : textColoursIn(page)) {
                assertThat(PALETTE)
                        .as("%s uses text-%s, which this check has no hex for — "
                                + "add it to PALETTE rather than leaving it unverified",
                                page.getFileName(), token)
                        .containsKey(token);
            }
        }
    }

    @Test
    void everyDarkOnLightTextColourMeetsAaContrast() {
        for (Path page : PAGES) {
            for (String token : textColoursIn(page)) {
                if ("white".equals(token)) {
                    continue;
                }
                double ratio = contrastRatio(PALETTE.get(token), LIGHTEST_SURFACE);
                assertThat(ratio)
                        .as("%s: text-%s on %s is %.2f:1, below AA %.1f:1",
                                page.getFileName(), token, "gray-50", ratio, AA_NORMAL_TEXT)
                        .isGreaterThanOrEqualTo(AA_NORMAL_TEXT);
            }
        }
    }

    @Test
    void whiteTextIsOnlyUsedOnBackgroundsDarkEnoughToCarryIt() {
        Pattern whiteOnSomething = Pattern.compile("class=\"[^\"]*\\bbg-([a-z]+-\\d{2,3})\\b[^\"]*\\btext-white\\b");
        for (Path page : PAGES) {
            Matcher m = whiteOnSomething.matcher(read(page));
            while (m.find()) {
                String background = m.group(1);
                assertThat(PALETTE)
                        .as("%s puts white text on bg-%s, unknown to this check",
                                page.getFileName(), background)
                        .containsKey(background);
                double ratio = contrastRatio(PALETTE.get("white"), PALETTE.get(background));
                assertThat(ratio)
                        .as("%s: white on bg-%s is %.2f:1, below AA", page.getFileName(), background, ratio)
                        .isGreaterThanOrEqualTo(AA_NORMAL_TEXT);
            }
        }
    }
}
