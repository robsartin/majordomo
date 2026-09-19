package com.majordomo.adapter.out.metadata.librarian;

import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.Confidence;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenLibraryClientTest {

    private MockWebServer server;
    private OpenLibraryClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
        var http = RestClient.builder().requestFactory(factory).build();
        client = new OpenLibraryClient(http, server.url("/").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private Book book(String title, String author) {
        var b = new Book();
        b.setId(com.majordomo.domain.model.UuidFactory.newId());
        b.setTitle(title);
        b.setAuthors(List.of(author));
        return b;
    }

    private void respond(String json) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json));
    }

    @Test
    void sourceName_identifiesTheCatalogOnEveryCandidateItProduces() {
        assertThat(client.sourceName()).isEqualTo("OPEN_LIBRARY");
    }

    @Test
    void findCandidates_mapsAMatchingDocToAScoredCandidate() {
        respond("""
                {"docs":[{"key":"/works/OL123W","title":"Refactoring",
                          "author_name":["Martin Fowler"],"first_publish_year":1999,
                          "publisher":["Addison-Wesley"],"isbn":["9780134757599"]}]}
                """);

        var candidates = client.findCandidates(book("Refactoring", "Martin Fowler"));

        assertThat(candidates).hasSize(1);
        var c = candidates.getFirst();
        assertThat(c.source()).isEqualTo("OPEN_LIBRARY");
        assertThat(c.externalId()).isEqualTo("/works/OL123W");
        assertThat(c.score()).isEqualTo(1.0);
        assertThat(c.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(c.payload()).containsEntry("publisher", "Addison-Wesley");
        assertThat(c.payload()).containsEntry("isbn13", "9780134757599");
        assertThat(c.isPending()).isTrue();
    }

    @Test
    void findCandidates_ordersBestScoringFirst() {
        respond("""
                {"docs":[
                  {"key":"/works/OL1W","title":"Something Else","author_name":["Nobody"]},
                  {"key":"/works/OL2W","title":"Refactoring","author_name":["Martin Fowler"]}]}
                """);

        var candidates = client.findCandidates(book("Refactoring", "Martin Fowler"));

        assertThat(candidates).hasSize(2);
        assertThat(candidates.getFirst().externalId()).isEqualTo("/works/OL2W");
        assertThat(candidates.getFirst().score())
                .isGreaterThan(candidates.get(1).score());
    }

    @Test
    void findCandidates_returnsEmptyWhenTheCatalogKnowsNothing() {
        respond("{\"docs\":[]}");

        assertThat(client.findCandidates(book("Obscure Monograph", "Nobody"))).isEmpty();
    }

    @Test
    void findCandidates_returnsEmptyRatherThanThrowingOnAnEmptyBody() {
        respond("{}");

        assertThat(client.findCandidates(book("Refactoring", "Martin Fowler"))).isEmpty();
    }

    @Test
    void findCandidates_throwsOnAServerErrorSoTheCircuitBreakerCanSeeIt() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> client.findCandidates(book("Refactoring", "Martin Fowler")))
                .isInstanceOf(MetadataSourceException.class);
    }

    @Test
    void findCandidates_throwsOnMalformedJsonRatherThanReportingNoMatches() {
        respond("{\"docs\": not json]");

        assertThatThrownBy(() -> client.findCandidates(book("Refactoring", "Martin Fowler")))
                .isInstanceOf(MetadataSourceException.class);
    }
}
