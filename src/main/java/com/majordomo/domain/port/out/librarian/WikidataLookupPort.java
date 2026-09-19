package com.majordomo.domain.port.out.librarian;

import com.majordomo.domain.model.librarian.Book;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound port for resolving a book and its authors to Wikidata QIDs.
 *
 * <p>The QID is the join key into the interest graph, so a wrong one is
 * corrosive in a way a wrong publisher is not. Implementations must return
 * empty rather than guessing when a title is ambiguous across several works;
 * an ambiguous match belongs in the review queue, not in the catalog.
 */
public interface WikidataLookupPort {

    /**
     * Resolves a book to its Wikidata QID.
     *
     * @param book the book to resolve
     * @return the QID, or empty when unresolved or ambiguous
     */
    Optional<String> findQid(Book book);

    /**
     * Resolves author names to their Wikidata QIDs.
     *
     * <p>The QID is what the interest graph joins on — Segue's {@code add_entity}
     * takes a QID, and what Librarian syncs is authors rather than works — so
     * this is the lookup that gates the sync in #319.
     *
     * <p>An author who cannot be resolved, or whose name is ambiguous across
     * several people, is simply absent from the result. A wrong person is worse
     * than a missing one: it attributes someone else's work in a graph that
     * outlives this catalog.
     *
     * @param authorNames the names to resolve
     * @return name to QID for those resolved unambiguously, in the order the
     *         names were given; never null
     */
    Map<String, String> findAuthorQids(List<String> authorNames);
}
