package com.majordomo.domain.model.event;

import com.majordomo.domain.model.UuidFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LibrarianEventsTest {

    @Test
    @DisplayName("BookCataloged records the new book and where the row came from")
    void shouldRecordProvenanceWhenBookCataloged() {
        var bookId = UuidFactory.newId();
        var orgId = UuidFactory.newId();
        var now = Instant.now();

        var event = new BookCataloged(bookId, orgId, "Networks (Second Edition)", "1", now);

        assertThat(event.bookId()).isEqualTo(bookId);
        assertThat(event.organizationId()).isEqualTo(orgId);
        assertThat(event.title()).isEqualTo("Networks (Second Edition)");
        assertThat(event.sourcePhoto()).isEqualTo("1");
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("BookEnriched names the source and the identifier that was applied")
    void shouldNameSourceAndIdentifierWhenBookEnriched() {
        var bookId = UuidFactory.newId();
        var orgId = UuidFactory.newId();
        var now = Instant.now();

        var event = new BookEnriched(bookId, orgId, "OPEN_LIBRARY", "OL7353617M", now);

        assertThat(event.source()).isEqualTo("OPEN_LIBRARY");
        assertThat(event.externalId()).isEqualTo("OL7353617M");
        assertThat(event.bookId()).isEqualTo(bookId);
        assertThat(event.organizationId()).isEqualTo(orgId);
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("BookSyncedToInterestGraph records which authors were pushed and under which QID")
    void shouldRecordAuthorsAndQidWhenSyncedToInterestGraph() {
        var bookId = UuidFactory.newId();
        var orgId = UuidFactory.newId();
        var now = Instant.now();

        var event = new BookSyncedToInterestGraph(
                bookId, orgId, "Q7000573", List.of("Mark Newman"), 4, now);

        assertThat(event.wikidataQid()).isEqualTo("Q7000573");
        assertThat(event.authors()).containsExactly("Mark Newman");
        assertThat(event.rating()).isEqualTo(4);
        assertThat(event.occurredAt()).isEqualTo(now);
    }
}
