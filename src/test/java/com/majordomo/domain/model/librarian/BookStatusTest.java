package com.majordomo.domain.model.librarian;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookStatusTest {

    @Test
    void bookStatus_coversFourShelfStates() {
        assertThat(BookStatus.values())
                .containsExactly(BookStatus.OWNED, BookStatus.READ, BookStatus.WANT, BookStatus.LOANED);
    }
}
