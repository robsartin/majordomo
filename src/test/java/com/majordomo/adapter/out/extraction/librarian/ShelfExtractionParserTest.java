package com.majordomo.adapter.out.extraction.librarian;

import com.majordomo.domain.model.librarian.ImportSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShelfExtractionParserTest {

    private final ShelfExtractionParser parser = new ShelfExtractionParser();

    @Test
    void parse_readsTitleAuthorAndNoteForEachBook() {
        var rows = parser.parse("""
                {"books":[
                  {"title":"Refactoring","author":"Martin Fowler","note":""},
                  {"title":"C++ Primer","author":"Stanley B. Lippman","note":"spine partly obscured"}]}
                """, "2");

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().title()).isEqualTo("Refactoring");
        assertThat(rows.getFirst().author()).isEqualTo("Martin Fowler");
        assertThat(rows.get(1).notes()).isEqualTo("spine partly obscured");
    }

    @Test
    void parse_marksEveryRowAsPhotoExtracted() {
        var rows = parser.parse("{\"books\":[{\"title\":\"A\",\"author\":\"B\",\"note\":\"\"}]}", "1");

        assertThat(rows).allSatisfy(r ->
                assertThat(r.source()).isEqualTo(ImportSource.PHOTO_EXTRACTION));
    }

    @Test
    void parse_neverCarriesARatingFromTheModel() {
        var rows = parser.parse("""
                {"books":[{"title":"A","author":"B","note":"","rating":5}]}
                """, "1");

        assertThat(rows.getFirst().rating())
                .as("a rating is the owner's judgement; a model must not invent one")
                .isNull();
    }

    @Test
    void parse_stampsTheSourcePhotoOnEveryRow() {
        var rows = parser.parse("{\"books\":[{\"title\":\"A\",\"author\":\"B\",\"note\":\"\"}]}", "shelf-3");

        assertThat(rows.getFirst().photo()).isEqualTo("shelf-3");
    }

    @Test
    void parse_toleratesAnEmptyShelf() {
        assertThat(parser.parse("{\"books\":[]}", "1")).isEmpty();
    }

    @Test
    void parse_skipsARowWithNoTitleRatherThanCatalogingABlank() {
        var rows = parser.parse("""
                {"books":[{"title":"","author":"B","note":""},{"title":"Real","author":"C","note":""}]}
                """, "1");

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().title()).isEqualTo("Real");
    }

    @Test
    void parse_failsLoudlyOnMalformedJsonRatherThanReturningNoBooks() {
        assertThatThrownBy(() -> parser.parse("{\"books\": not json]", "1"))
                .isInstanceOf(ShelfExtractionException.class);
    }

    @Test
    void parse_failsLoudlyWhenTheModelOmitsTheBooksArray() {
        assertThatThrownBy(() -> parser.parse("{\"note\":\"I could not read the photo\"}", "1"))
                .isInstanceOf(ShelfExtractionException.class);
    }

    @Test
    void parse_unwrapsJsonFencedInAMarkdownCodeBlock() {
        var rows = parser.parse("""
                ```json
                {"books":[{"title":"Fenced","author":"B","note":""}]}
                ```
                """, "1");

        assertThat(rows.getFirst().title()).isEqualTo("Fenced");
    }
}
