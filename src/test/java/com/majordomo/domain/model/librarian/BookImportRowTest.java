package com.majordomo.domain.model.librarian;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookImportRowTest {

    @Test
    void bookImportRow_carriesRawColumnsVerbatim() {
        var row = new BookImportRow(
                "Digital Accessibility Ethics",
                "Lainey Feingold, Regine Gilbert",
                "2",
                "Author not visible in photo; filled from knowledge",
                3, ImportSource.CSV);

        assertThat(row.title()).isEqualTo("Digital Accessibility Ethics");
        assertThat(row.author()).isEqualTo("Lainey Feingold, Regine Gilbert");
        assertThat(row.photo()).isEqualTo("2");
        assertThat(row.notes()).contains("filled from knowledge");
        assertThat(row.rating()).isEqualTo(3);
    }

    @Test
    void bookImportRow_allowsNullRatingWhenColumnBlank() {
        var row = new BookImportRow("Refactoring", "Martin Fowler", "2", "", null, ImportSource.CSV);

        assertThat(row.rating()).isNull();
    }
}
