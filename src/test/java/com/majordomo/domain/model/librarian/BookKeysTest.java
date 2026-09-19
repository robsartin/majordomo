package com.majordomo.domain.model.librarian;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookKeysTest {

    @Test
    void splitAuthors_returnsSingleAuthorUnchanged() {
        assertThat(BookKeys.splitAuthors("Mark Newman")).containsExactly("Mark Newman");
    }

    @Test
    void splitAuthors_splitsOnAmpersand() {
        assertThat(BookKeys.splitAuthors("Mark Needham & Amy Hodler"))
                .containsExactly("Mark Needham", "Amy Hodler");
    }

    @Test
    void splitAuthors_splitsOnCommaAndAmpersandTogether() {
        assertThat(BookKeys.splitAuthors("Ian Robinson, Jim Webber & Emil Eifrem"))
                .containsExactly("Ian Robinson", "Jim Webber", "Emil Eifrem");
    }

    @Test
    void splitAuthors_dropsTrailingEditorMarker() {
        assertThat(BookKeys.splitAuthors("Mark Newman, Albert-László Barabási & Duncan J. Watts (eds.)"))
                .containsExactly("Mark Newman", "Albert-László Barabási", "Duncan J. Watts");
    }

    @Test
    void splitAuthors_returnsEmptyListForBlankInput() {
        assertThat(BookKeys.splitAuthors("  ")).isEmpty();
        assertThat(BookKeys.splitAuthors(null)).isEmpty();
    }

    @Test
    void normalize_ignoresCasePunctuationAndSpacing() {
        String a = BookKeys.normalize("Networks (Second Edition)", List.of("Mark Newman"));
        String b = BookKeys.normalize("  networks   second edition ", List.of("mark  newman"));

        assertThat(a).isEqualTo(b);
    }

    @Test
    void normalize_ignoresLeadingArticleInTitle() {
        assertThat(BookKeys.normalize("The Practice of Programming", List.of("Brian W. Kernighan")))
                .isEqualTo(BookKeys.normalize("Practice of Programming", List.of("Brian W. Kernighan")));
    }

    @Test
    void normalize_foldsAccentsSoTheKeyIsStable() {
        assertThat(BookKeys.normalize("Networks", List.of("Albert-László Barabási")))
                .isEqualTo(BookKeys.normalize("Networks", List.of("Albert-Laszlo Barabasi")));
    }

    @Test
    void normalize_distinguishesDifferentAuthorsOfTheSameTitle() {
        assertThat(BookKeys.normalize("Networks", List.of("Mark Newman")))
                .isNotEqualTo(BookKeys.normalize("Networks", List.of("Someone Else")));
    }

    @Test
    void normalize_isStableRegardlessOfAuthorOrder() {
        assertThat(BookKeys.normalize("Graph Algorithms", List.of("Mark Needham", "Amy Hodler")))
                .isEqualTo(BookKeys.normalize("Graph Algorithms", List.of("Amy Hodler", "Mark Needham")));
    }

    @Test
    void normalize_toleratesMissingAuthors() {
        assertThat(BookKeys.normalize("Orphan Title", List.of())).isNotBlank();
    }
}
