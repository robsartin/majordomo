package com.majordomo.domain.model.librarian;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pure functions for turning a transcribed title and author string into the
 * shapes the catalog needs: a list of author names, and the normalised dedupe
 * key that import matches on.
 *
 * <p>This is deliberately the single implementation of the normalisation. The
 * key is computed here and stored on the book rather than derived by a database
 * generated column, so the importer and the repository lookup cannot drift
 * apart (ADR-0023).
 */
public final class BookKeys {

    private static final List<String> LEADING_ARTICLES = List.of("the ", "a ", "an ");
    private static final String EDITOR_MARKER = "\\s*\\((?:eds?\\.?|editors?)\\)\\s*$";

    private BookKeys() {
    }

    /**
     * Splits a transcribed author field into individual names.
     *
     * <p>The shelf CSV writes multiple authors as {@code "A, B & C"}. Splitting
     * on comma is safe for this data — every comma-separated row also contains
     * an ampersand, so no row uses a {@code "Last, First"} convention that the
     * split would corrupt. A trailing editor marker such as {@code (eds.)} is
     * dropped.
     *
     * @param authorField the raw author string, may be null or blank
     * @return the individual author names, empty when there are none
     */
    public static List<String> splitAuthors(String authorField) {
        if (authorField == null || authorField.isBlank()) {
            return List.of();
        }
        String cleaned = authorField.replaceAll(EDITOR_MARKER, "");
        return Arrays.stream(cleaned.split("[,&]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Builds the dedupe key for a title and its authors.
     *
     * <p>Case, punctuation, accents, internal spacing, a leading article on the
     * title, and author order are all ignored, so the same book transcribed
     * twice produces one key.
     *
     * @param title   the book title
     * @param authors the author names
     * @return the normalised key
     */
    public static String normalize(String title, List<String> authors) {
        String normalizedTitle = stripLeadingArticle(fold(title));
        String normalizedAuthors = authors == null ? "" : authors.stream()
                .map(BookKeys::fold)
                .filter(s -> !s.isEmpty())
                .sorted()
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        return normalizedTitle + "|" + normalizedAuthors;
    }

    /**
     * Splits a value into folded comparison tokens — lowercased, accent-stripped
     * and punctuation-free. Shared with match scoring so comparison and dedupe
     * agree on what counts as the same word.
     *
     * @param value the text to tokenise, may be null
     * @return the tokens, empty when there are none
     */
    public static Set<String> tokens(String value) {
        String folded = fold(value);
        if (folded.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(folded.split(" ")));
    }

    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static String stripLeadingArticle(String folded) {
        for (String article : LEADING_ARTICLES) {
            if (folded.startsWith(article)) {
                return folded.substring(article.length());
            }
        }
        return folded;
    }
}
