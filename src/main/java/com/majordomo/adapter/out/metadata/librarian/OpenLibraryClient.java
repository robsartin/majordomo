package com.majordomo.adapter.out.metadata.librarian;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;
import com.majordomo.domain.model.librarian.MatchScorer;
import com.majordomo.domain.port.out.librarian.BookMetadataSource;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks books up in Open Library's search API.
 *
 * <p>Returns candidates rather than applying anything. Open Library's coverage
 * of older technical books is uneven, so finding nothing is an ordinary outcome
 * and comes back as an empty list; only a transport or parse failure throws, so
 * the circuit breaker sees real faults and not absent books.
 */
@Component
public class OpenLibraryClient implements BookMetadataSource {

    private static final String SOURCE = "OPEN_LIBRARY";
    private static final int MAX_CANDIDATES = 5;

    private final RestClient http;
    private final String baseUrl;

    /**
     * Constructs the client.
     *
     * @param http    shared HTTP client for metadata lookups
     * @param baseUrl Open Library base URL
     */
    public OpenLibraryClient(@Qualifier("metadataRestClient") RestClient http,
                             @Value("${librarian.open-library.base-url:https://openlibrary.org}")
                             String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    @CircuitBreaker(name = "librarian-metadata")
    @Retry(name = "librarian-metadata")
    public List<EnrichmentCandidate> findCandidates(Book book) {
        SearchResponse response;
        try {
            response = http.get()
                    .uri(baseUrl + "/search.json?title={title}&author={author}&limit={limit}",
                            book.getTitle(),
                            book.getAuthors() == null || book.getAuthors().isEmpty()
                                    ? "" : String.join(" ", book.getAuthors()),
                            MAX_CANDIDATES)
                    .retrieve()
                    .body(SearchResponse.class);
        } catch (RuntimeException e) {
            throw new MetadataSourceException(
                    "Open Library lookup failed for '" + book.getTitle() + "'", e);
        }
        if (response == null || response.docs() == null) {
            return List.of();
        }
        return response.docs().stream()
                .map(doc -> toCandidate(book, doc))
                .sorted(Comparator.comparingDouble(EnrichmentCandidate::score).reversed())
                .toList();
    }

    private EnrichmentCandidate toCandidate(Book book, Doc doc) {
        List<String> authors = doc.authorName() == null ? List.of() : doc.authorName();
        double score = MatchScorer.score(book, doc.title(), authors);
        return new EnrichmentCandidate(
                UuidFactory.newId(),
                book.getId(),
                SOURCE,
                doc.key(),
                score,
                MatchScorer.confidenceFor(score),
                payloadOf(doc),
                Instant.now(),
                null,
                null);
    }

    private Map<String, String> payloadOf(Doc doc) {
        var payload = new LinkedHashMap<String, String>();
        putIfPresent(payload, "title", doc.title());
        if (doc.authorName() != null && !doc.authorName().isEmpty()) {
            payload.put("authors", String.join(" & ", doc.authorName()));
        }
        if (doc.publisher() != null && !doc.publisher().isEmpty()) {
            payload.put("publisher", doc.publisher().getFirst());
        }
        if (doc.isbn() != null && !doc.isbn().isEmpty()) {
            payload.put("isbn13", doc.isbn().getFirst());
        }
        if (doc.firstPublishYear() != null) {
            payload.put("year", String.valueOf(doc.firstPublishYear()));
        }
        return Map.copyOf(payload);
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<Doc> docs) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Doc(
            String key,
            String title,
            @JsonProperty("author_name") List<String> authorName,
            @JsonProperty("first_publish_year") Integer firstPublishYear,
            List<String> publisher,
            List<String> isbn) { }
}
