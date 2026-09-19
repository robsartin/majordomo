package com.majordomo.adapter.in.ingest.librarian;

import com.majordomo.domain.model.librarian.BookImportRow;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookCsvReaderTest {

    private static final String HEADER = "Title,Author,Photo,Notes,Rating\n";

    private List<BookImportRow> read(String csv) {
        return new BookCsvReader().read(new StringReader(csv));
    }

    @Test
    void read_parsesTitleAuthorPhotoNotesAndRating() {
        var rows = read(HEADER + "Refactoring,Martin Fowler,2,,5\n");

        assertThat(rows).hasSize(1);
        var row = rows.getFirst();
        assertThat(row.title()).isEqualTo("Refactoring");
        assertThat(row.author()).isEqualTo("Martin Fowler");
        assertThat(row.photo()).isEqualTo("2");
        assertThat(row.rating()).isEqualTo(5);
    }

    @Test
    void read_treatsBlankRatingAsUnrated() {
        var rows = read(HEADER + "Refactoring,Martin Fowler,2,,\n");

        assertThat(rows.getFirst().rating()).isNull();
    }

    @Test
    void read_keepsQuotedFieldsContainingCommas() {
        var rows = read(HEADER
                + "\"Networks, Crowds, and Markets\",\"David Easley & Jon Kleinberg\",1,,4\n");

        assertThat(rows.getFirst().title()).isEqualTo("Networks, Crowds, and Markets");
        assertThat(rows.getFirst().author()).isEqualTo("David Easley & Jon Kleinberg");
    }

    @Test
    void read_failsLoudlyOnARowWithTooFewColumns() {
        assertThatThrownBy(() -> read(HEADER + "Refactoring,Martin Fowler\n"))
                .isInstanceOf(BookCsvFormatException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void read_failsLoudlyOnANonNumericRating() {
        assertThatThrownBy(() -> read(HEADER + "Refactoring,Martin Fowler,2,,excellent\n"))
                .isInstanceOf(BookCsvFormatException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void read_failsLoudlyOnARatingOutsideTheScale() {
        assertThatThrownBy(() -> read(HEADER + "Refactoring,Martin Fowler,2,,9\n"))
                .isInstanceOf(BookCsvFormatException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void read_failsLoudlyOnABlankTitle() {
        assertThatThrownBy(() -> read(HEADER + " ,Martin Fowler,2,,4\n"))
                .isInstanceOf(BookCsvFormatException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void read_acceptsTheRealSeedShelfAndProducesUniqueDedupeKeys() throws IOException {
        Path seed = Path.of("doc/librarian/bookshelf-2026-09-18.csv");
        try (var reader = Files.newBufferedReader(seed)) {
            var rows = new BookCsvReader().read(reader);

            assertThat(rows).hasSize(57);
            assertThat(rows).allSatisfy(r -> assertThat(r.rating()).isBetween(1, 5));

            var keys = rows.stream()
                    .map(r -> com.majordomo.domain.model.librarian.BookKeys.normalize(
                            r.title(), com.majordomo.domain.model.librarian.BookKeys.splitAuthors(r.author())))
                    .toList();
            assertThat(keys).doesNotHaveDuplicates();
        }
    }
}
