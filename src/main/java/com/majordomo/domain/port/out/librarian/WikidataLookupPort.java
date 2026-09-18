package com.majordomo.domain.port.out.librarian;

import com.majordomo.domain.model.librarian.Book;

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
}
