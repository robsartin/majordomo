package com.majordomo.adapter.out.interestgraph.librarian;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.librarian.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SegueAdapterTest {

    /** Records the calls a real client would have made. */
    private static final class RecordingClient extends SegueMcpClient {
        private final List<String> calls = new ArrayList<>();
        private final List<Map<String, Object>> arguments = new ArrayList<>();

        RecordingClient() {
            super(null, "http://unused/mcp");
        }

        @Override
        public String callTool(String toolName, Map<String, Object> args) {
            calls.add(toolName);
            arguments.add(args);
            return "ok";
        }
    }

    private RecordingClient client;
    private SegueAdapter adapter;

    @BeforeEach
    void setUp() {
        client = new RecordingClient();
        adapter = new SegueAdapter(client);
    }

    private Book book(String title, Integer rating) {
        var b = new Book();
        b.setId(UuidFactory.newId());
        b.setTitle(title);
        b.setRating(rating);
        return b;
    }

    @Test
    void syncBook_addsTheEntityBeforeNotingAffinity() {
        adapter.syncBook(book("Networks", 4), Map.of("Mark Newman", "Q1934063"));

        assertThat(client.calls)
                .as("note_affinity requires the entity to exist first")
                .containsExactly("add_entity", "note_affinity");
    }

    @Test
    void syncBook_sendsTheOwnersRatingAsTheAffinity() {
        adapter.syncBook(book("Networks", 4), Map.of("Mark Newman", "Q1934063"));

        assertThat(client.arguments.get(1)).containsEntry("rating", 4);
        assertThat(client.arguments.get(1)).containsEntry("qid", "Q1934063");
    }

    @Test
    void syncBook_addsAnUnratedAuthorWithoutInventingAnAffinity() {
        adapter.syncBook(book("Unrated Book", null), Map.of("Someone", "Q999"));

        assertThat(client.calls)
                .as("a rating is a claim the owner has not made")
                .containsExactly("add_entity");
    }

    @Test
    void syncBook_pushesEveryResolvedAuthor() {
        var authors = new LinkedHashMap<String, String>();
        authors.put("Mark Needham", "Q1");
        authors.put("Amy Hodler", "Q2");

        adapter.syncBook(book("Graph Algorithms", 5), authors);

        assertThat(client.calls).containsExactly(
                "add_entity", "note_affinity", "add_entity", "note_affinity");
    }

    @Test
    void syncBook_doesNothingWhenNoAuthorResolved() {
        adapter.syncBook(book("Orphan", 5), Map.of());

        assertThat(client.calls)
                .as("a book with no QID must never be synced under a guessed identifier")
                .isEmpty();
    }
}
