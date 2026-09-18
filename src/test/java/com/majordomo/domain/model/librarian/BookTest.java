package com.majordomo.domain.model.librarian;

import com.majordomo.domain.model.UuidFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookTest {

    @Test
    @DisplayName("Book carries catalog, enrichment and provenance fields together")
    void shouldRetainEveryCatalogFieldWhenPopulated() {
        var id = UuidFactory.newId();
        var orgId = UuidFactory.newId();
        var now = Instant.now();

        var book = new Book();
        book.setId(id);
        book.setOrganizationId(orgId);
        book.setTitle("Networks (Second Edition)");
        book.setAuthors(List.of("Mark Newman"));
        book.setLocation("bookcase 1, shelf 2");
        book.setStatus(BookStatus.OWNED);
        book.setRating(4);
        book.setConfidence(Confidence.HIGH);
        book.setSourcePhoto("1");
        book.setWikidataQid("Q7000573");
        book.setCreatedAt(now);

        assertThat(book.getId()).isEqualTo(id);
        assertThat(book.getOrganizationId()).isEqualTo(orgId);
        assertThat(book.getTitle()).isEqualTo("Networks (Second Edition)");
        assertThat(book.getAuthors()).containsExactly("Mark Newman");
        assertThat(book.getLocation()).isEqualTo("bookcase 1, shelf 2");
        assertThat(book.getStatus()).isEqualTo(BookStatus.OWNED);
        assertThat(book.getRating()).isEqualTo(4);
        assertThat(book.getConfidence()).isEqualTo(Confidence.HIGH);
        assertThat(book.getSourcePhoto()).isEqualTo("1");
        assertThat(book.getWikidataQid()).isEqualTo("Q7000573");
        assertThat(book.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("An unrated book is valid — rating stays null until the owner sets one")
    void shouldLeaveRatingNullWhenNeverSet() {
        var book = new Book();
        book.setTitle("Refactoring");

        assertThat(book.getRating()).isNull();
    }
}
