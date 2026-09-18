package com.majordomo.domain.model.librarian;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookStatusTest {

    @Test
    @DisplayName("BookStatus covers the four shelf states a book can be in")
    void shouldExposeFourShelfStatesWhenEnumerated() {
        assertThat(BookStatus.values())
                .containsExactly(BookStatus.OWNED, BookStatus.READ, BookStatus.WANT, BookStatus.LOANED);
    }
}
