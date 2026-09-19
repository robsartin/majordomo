package com.majordomo.adapter.out.wikidata.librarian;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.port.out.librarian.WikidataLookupPort;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves books and authors to Wikidata QIDs over the public SPARQL endpoint.
 *
 * <p>The QID is the join key into the interest graph, so a wrong one is
 * corrosive in a way a wrong publisher is not: it attributes someone else's work
 * in a graph that outlives this catalog. Every method here returns nothing
 * rather than guessing when a name resolves to more than one entity.
 */
@Component
public class WikidataSparqlClient implements WikidataLookupPort {

    private static final Logger LOG = LoggerFactory.getLogger(WikidataSparqlClient.class);
    private static final String ENTITY_PREFIX = "http://www.wikidata.org/entity/";

    /**
     * One match means one answer. Two or more is ambiguity, and the caller is
     * told nothing rather than a coin-flip; ambiguity belongs in the review
     * queue.
     */
    private static final int UNAMBIGUOUS = 1;

    private final RestClient http;
    private final String endpoint;

    /**
     * Constructs the client.
     *
     * @param http     shared HTTP client for metadata lookups
     * @param endpoint the SPARQL endpoint URL
     */
    public WikidataSparqlClient(@Qualifier("metadataRestClient") RestClient http,
                                @Value("${librarian.wikidata.endpoint:https://query.wikidata.org/sparql}")
                                String endpoint) {
        this.http = http;
        this.endpoint = endpoint;
    }

    @Override
    @CircuitBreaker(name = "librarian-metadata")
    @Retry(name = "librarian-metadata")
    public Optional<String> findQid(Book book) {
        List<String> authors = book.getAuthors();
        if (authors == null || authors.isEmpty()) {
            // Titles are not unique; without an author there is nothing to
            // disambiguate with, and a lone-title match would be a guess.
            LOG.debug("Skipping Wikidata work lookup for '{}': no author recorded", book.getTitle());
            return Optional.empty();
        }
        List<String> matches = select(workQuery(book.getTitle(), authors.getFirst()));
        if (matches.size() != UNAMBIGUOUS) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    @Override
    @CircuitBreaker(name = "librarian-metadata")
    @Retry(name = "librarian-metadata")
    public Map<String, String> findAuthorQids(List<String> authorNames) {
        if (authorNames == null || authorNames.isEmpty()) {
            return Map.of();
        }
        var resolved = new LinkedHashMap<String, String>();
        for (String name : authorNames) {
            List<String> matches = select(personQuery(name));
            if (matches.size() == UNAMBIGUOUS) {
                resolved.put(name, matches.getFirst());
            } else {
                LOG.debug("Not resolving author '{}': {} matches", name, matches.size());
            }
        }
        // Not Map.copyOf: that returns an unordered map whose iteration order
        // Java randomises per JVM. Author order is meaningful — the first
        // author is not interchangeable with the third — so it is preserved.
        return Collections.unmodifiableMap(resolved);
    }

    private List<String> select(String sparql) {
        SparqlResults results;
        try {
            // The query goes through a template variable rather than being
            // concatenated into the URL: SPARQL is full of braces, and
            // UriComponentsBuilder reads a literal brace as a URI template.
            String uri = UriComponentsBuilder.fromUriString(endpoint)
                    .queryParam("query", "{q}")
                    .queryParam("format", "json")
                    .build(sparql)
                    .toString();
            results = http.get()
                    .uri(uri)
                    .header("Accept", "application/sparql-results+json")
                    .retrieve()
                    .body(SparqlResults.class);
        } catch (RuntimeException e) {
            throw new WikidataLookupException("Wikidata lookup failed", e);
        }
        if (results == null || results.results() == null || results.results().bindings() == null) {
            return List.of();
        }
        return results.results().bindings().stream()
                .map(b -> b.item() == null ? null : b.item().value())
                .filter(v -> v != null && v.startsWith(ENTITY_PREFIX))
                .map(v -> v.substring(ENTITY_PREFIX.length()))
                .distinct()
                .toList();
    }

    private String workQuery(String title, String author) {
        return """
                SELECT DISTINCT ?item WHERE {
                  ?item rdfs:label ?title ; wdt:P50 ?author .
                  ?author rdfs:label ?authorLabel .
                  FILTER(LCASE(STR(?title)) = LCASE(%s))
                  FILTER(LCASE(STR(?authorLabel)) = LCASE(%s))
                } LIMIT 5
                """.formatted(literal(title), literal(author));
    }

    private String personQuery(String name) {
        return """
                SELECT DISTINCT ?item WHERE {
                  ?item wdt:P31 wd:Q5 ; rdfs:label ?label .
                  FILTER(LCASE(STR(?label)) = LCASE(%s))
                } LIMIT 5
                """.formatted(literal(name));
    }

    private String literal(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SparqlResults(Results results) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Results(List<Binding> bindings) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Binding(Cell item) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Cell(String value) { }
}
