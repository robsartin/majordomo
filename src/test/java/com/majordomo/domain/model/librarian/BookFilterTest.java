package com.majordomo.domain.model.librarian;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookFilterTest {

    @Test
    void none_hasNoCriteriaSet() {
        var filter = BookFilter.none();

        assertThat(filter.status()).isNull();
        assertThat(filter.confidence()).isNull();
        assertThat(filter.query()).isNull();
        assertThat(filter.isEmpty()).isTrue();
    }

    @Test
    void none_returnsTheSameInstance() {
        assertThat(BookFilter.none()).isSameAs(BookFilter.none());
    }

    @Test
    void isEmpty_isFalseOnceAnyCriterionIsSet() {
        assertThat(new BookFilter(BookStatus.OWNED, null, null).isEmpty()).isFalse();
        assertThat(new BookFilter(null, Confidence.LOW, null).isEmpty()).isFalse();
        assertThat(new BookFilter(null, null, "networks").isEmpty()).isFalse();
    }

    @Test
    void blankQueryIsNormalisedToNullSoItDoesNotMatchEverything() {
        assertThat(new BookFilter(null, null, "   ").query()).isNull();
        assertThat(new BookFilter(null, null, "   ").isEmpty()).isTrue();
    }

    @Test
    void queryIsTrimmed() {
        assertThat(new BookFilter(null, null, "  networks ").query()).isEqualTo("networks");
    }
}
