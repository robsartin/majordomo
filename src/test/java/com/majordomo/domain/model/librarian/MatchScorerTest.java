package com.majordomo.domain.model.librarian;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchScorerTest {

    private Book book(String title, String... authors) {
        var b = new Book();
        b.setTitle(title);
        b.setAuthors(List.of(authors));
        return b;
    }

    @Test
    void score_isPerfectWhenTitleAndAuthorMatchExactly() {
        double s = MatchScorer.score(book("Refactoring", "Martin Fowler"),
                "Refactoring", List.of("Martin Fowler"));

        assertThat(s).isEqualTo(1.0);
    }

    @Test
    void score_ignoresCaseAccentsAndPunctuation() {
        double s = MatchScorer.score(book("Networks", "Albert-László Barabási"),
                "networks", List.of("Albert-Laszlo Barabasi"));

        assertThat(s).isEqualTo(1.0);
    }

    @Test
    void score_dropsBelowAutoApplyWhenTheAuthorIsWrong() {
        double s = MatchScorer.score(book("Refactoring", "Martin Fowler"),
                "Refactoring", List.of("Kent Beck"));

        assertThat(s).isLessThan(MatchScorer.AUTO_APPLY_THRESHOLD);
    }

    @Test
    void score_isLowWhenTheTitleIsUnrelated() {
        double s = MatchScorer.score(book("Refactoring", "Martin Fowler"),
                "Cooking for Geeks", List.of("Jeff Potter"));

        assertThat(s).isLessThan(0.3);
    }

    @Test
    void score_toleratesAnExtraSubtitleOnTheCandidate() {
        double exact = MatchScorer.score(book("Clean Architecture", "Robert C. Martin"),
                "Clean Architecture", List.of("Robert C. Martin"));
        double withSubtitle = MatchScorer.score(book("Clean Architecture", "Robert C. Martin"),
                "Clean Architecture: A Craftsman's Guide", List.of("Robert C. Martin"));

        assertThat(withSubtitle).isLessThan(exact).isGreaterThan(0.6);
    }

    @Test
    void score_doesNotRewardAMatchWhenTheBookHasNoAuthorRecorded() {
        double s = MatchScorer.score(book("Orphan Title"), "Orphan Title", List.of("Anyone At All"));

        assertThat(s).isLessThan(MatchScorer.AUTO_APPLY_THRESHOLD);
    }

    @Test
    void confidenceFor_autoAppliesOnlyAtOrAboveTheThreshold() {
        assertThat(MatchScorer.confidenceFor(MatchScorer.AUTO_APPLY_THRESHOLD)).isEqualTo(Confidence.HIGH);
        assertThat(MatchScorer.confidenceFor(MatchScorer.AUTO_APPLY_THRESHOLD - 0.01))
                .isNotEqualTo(Confidence.HIGH);
    }

    @Test
    void confidenceFor_gradesAMiddlingScoreAsMedium() {
        assertThat(MatchScorer.confidenceFor(0.7)).isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void confidenceFor_gradesAWeakScoreAsLow() {
        assertThat(MatchScorer.confidenceFor(0.2)).isEqualTo(Confidence.LOW);
    }
}
