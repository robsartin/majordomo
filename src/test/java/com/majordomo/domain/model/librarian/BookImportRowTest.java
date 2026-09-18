package com.majordomo.domain.model.librarian;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookImportRowTest {

    @Test
    @DisplayName("BookImportRow carries one raw CSV row exactly as transcribed")
    void shouldCarryRawColumnsWhenBuiltFromCsv() {
        var row = new BookImportRow(
                "Digital Accessibility Ethics",
                "Lainey Feingold, Regine Gilbert",
                "2",
                "Author not visible in photo; filled from knowledge",
                3);

        assertThat(row.title()).isEqualTo("Digital Accessibility Ethics");
        assertThat(row.author()).isEqualTo("Lainey Feingold, Regine Gilbert");
        assertThat(row.photo()).isEqualTo("2");
        assertThat(row.notes()).contains("filled from knowledge");
        assertThat(row.rating()).isEqualTo(3);
    }

    @Test
    @DisplayName("A row with no rating yet is representable — the column may be blank")
    void shouldAllowNullRatingWhenColumnBlank() {
        var row = new BookImportRow("Refactoring", "Martin Fowler", "2", "", null);

        assertThat(row.rating()).isNull();
    }
}
