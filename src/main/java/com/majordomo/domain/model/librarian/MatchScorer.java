package com.majordomo.domain.model.librarian;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores how well an external catalog record matches a book on the shelf.
 *
 * <p>Title and author are weighted separately because they fail differently.
 * A catalog will happily return a plausible title for a half-read spine, so the
 * author is what distinguishes a real match from a confident-looking wrong one
 * — which is the failure mode the review queue exists to catch.
 *
 * <p>Only a score at or above {@link #AUTO_APPLY_THRESHOLD} is applied without
 * a human looking at it.
 */
public final class MatchScorer {

    /** At or above this, a match is applied automatically. */
    public static final double AUTO_APPLY_THRESHOLD = 0.90;

    /** At or above this (but below auto-apply), a match is worth reviewing. */
    public static final double REVIEW_THRESHOLD = 0.50;

    private static final double TITLE_WEIGHT = 0.6;
    private static final double AUTHOR_WEIGHT = 0.4;

    private MatchScorer() {
    }

    /**
     * Scores a candidate record against a book.
     *
     * @param book             the shelf book
     * @param candidateTitle   the external record's title
     * @param candidateAuthors the external record's authors
     * @return a score in [0, 1]
     */
    public static double score(Book book, String candidateTitle, List<String> candidateAuthors) {
        double title = overlap(BookKeys.tokens(book.getTitle()), BookKeys.tokens(candidateTitle));
        double author = overlap(authorTokens(book.getAuthors()), authorTokens(candidateAuthors));
        return TITLE_WEIGHT * title + AUTHOR_WEIGHT * author;
    }

    /**
     * Buckets a score for display and for the auto-apply decision.
     *
     * @param score a score in [0, 1]
     * @return the confidence bucket
     */
    public static Confidence confidenceFor(double score) {
        if (score >= AUTO_APPLY_THRESHOLD) {
            return Confidence.HIGH;
        }
        if (score >= REVIEW_THRESHOLD) {
            return Confidence.MEDIUM;
        }
        return Confidence.LOW;
    }

    private static Set<String> authorTokens(List<String> authors) {
        var all = new HashSet<String>();
        if (authors != null) {
            authors.forEach(a -> all.addAll(BookKeys.tokens(a)));
        }
        return all;
    }

    /**
     * Dice overlap rather than Jaccard. A catalog record routinely carries a
     * fuller title than the spine does — "Clean Architecture" against "Clean
     * Architecture: A Craftsman's Guide" — and Jaccard punishes those extra
     * tokens hard enough to send every such match to review. Dice is gentler
     * about them while still separating this shelf's "Networks" from its
     * "Networks, Crowds, and Markets".
     *
     * <p>An empty set scores 0 rather than 1: a book with no author recorded
     * must not be treated as agreeing with every candidate, which would push
     * unauthored rows straight past the review gate.
     */
    private static double overlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        var intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return 2.0 * intersection.size() / (a.size() + b.size());
    }
}
