package com.majordomo.adapter.out.wikidata.librarian;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.librarian.Book;
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

class WikidataSparqlClientTest {

    private MockWebServer server;
    private WikidataSparqlClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
        client = new WikidataSparqlClient(
                RestClient.builder().requestFactory(factory).build(),
                server.url("/sparql").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private Book book(String title, String... authors) {
        var b = new Book();
        b.setId(UuidFactory.newId());
        b.setTitle(title);
        b.setAuthors(List.of(authors));
        return b;
    }

    private void respondWith(String... qids) {
        var bindings = new StringBuilder();
        for (int i = 0; i < qids.length; i++) {
            if (i > 0) {
                bindings.append(',');
            }
            bindings.append("{\"item\":{\"value\":\"http://www.wikidata.org/entity/")
                    .append(qids[i]).append("\"}}");
        }
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/sparql-results+json")
                .setBody("{\"results\":{\"bindings\":[" + bindings + "]}}"));
    }

    @Test
    void findQid_returnsTheQidWhenExactlyOneWorkMatches() {
        respondWith("Q7000573");

        assertThat(client.findQid(book("Networks", "Mark Newman"))).contains("Q7000573");
    }

    @Test
    void findQid_returnsEmptyWhenTheTitleIsAmbiguousAcrossWorks() {
        respondWith("Q1", "Q2");

        assertThat(client.findQid(book("Networks", "Mark Newman"))).isEmpty();
    }

    @Test
    void findQid_returnsEmptyWhenWikidataKnowsNothing() {
        respondWith();

        assertThat(client.findQid(book("An Obscure Monograph", "Nobody"))).isEmpty();
    }

    @Test
    void findQid_returnsEmptyWhenTheBookHasNoAuthorToDisambiguateWith() {
        assertThat(client.findQid(book("Networks"))).isEmpty();
        assertThat(server.getRequestCount())
                .as("must not query Wikidata when there is nothing to disambiguate with")
                .isZero();
    }

    @Test
    void findAuthorQids_resolvesEachAuthorToItsOwnQid() {
        respondWith("Q1934063");
        respondWith("Q92828");

        var qids = client.findAuthorQids(List.of("Mark Newman", "Albert-László Barabási"));

        assertThat(qids).containsExactly(
                java.util.Map.entry("Mark Newman", "Q1934063"),
                java.util.Map.entry("Albert-László Barabási", "Q92828"));
    }

    @Test
    void findAuthorQids_omitsAnAmbiguousAuthorRatherThanPickingOne() {
        respondWith("Q1", "Q2");

        assertThat(client.findAuthorQids(List.of("John Smith"))).isEmpty();
    }

    @Test
    void findAuthorQids_omitsAnUnknownAuthorWithoutFailingTheRest() {
        respondWith();
        respondWith("Q1934063");

        var qids = client.findAuthorQids(List.of("Nobody At All", "Mark Newman"));

        assertThat(qids).containsExactly(java.util.Map.entry("Mark Newman", "Q1934063"));
    }

    @Test
    void findAuthorQids_returnsEmptyForNoAuthorsWithoutQuerying() {
        assertThat(client.findAuthorQids(List.of())).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void lookup_throwsOnAServerErrorSoTheCircuitBreakerCanSeeIt() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> client.findQid(book("Networks", "Mark Newman")))
                .isInstanceOf(WikidataLookupException.class);
    }

    @Test
    void lookup_throwsOnMalformedResultsRatherThanReportingNoMatch() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/sparql-results+json")
                .setBody("{\"results\": not json]"));

        assertThatThrownBy(() -> client.findQid(book("Networks", "Mark Newman")))
                .isInstanceOf(WikidataLookupException.class);
    }
}
