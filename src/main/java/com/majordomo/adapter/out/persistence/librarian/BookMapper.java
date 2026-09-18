package com.majordomo.adapter.out.persistence.librarian;

import com.majordomo.domain.model.librarian.Book;

/**
 * Maps between the {@link Book} domain model and {@link BookEntity}.
 */
public final class BookMapper {

    private BookMapper() {
    }

    /**
     * Converts a domain book to its persistence entity.
     *
     * @param book the domain book
     * @return the entity
     */
    public static BookEntity toEntity(Book book) {
        var e = new BookEntity();
        e.setId(book.getId());
        e.setOrganizationId(book.getOrganizationId());
        e.setTitle(book.getTitle());
        e.setSubtitle(book.getSubtitle());
        e.setEdition(book.getEdition());
        e.setAuthors(book.getAuthors());
        e.setIsbn13(book.getIsbn13());
        e.setPublisher(book.getPublisher());
        e.setYear(book.getYear());
        e.setLocation(book.getLocation());
        e.setNormalizedKey(book.getNormalizedKey());
        e.setCopies(book.getCopies());
        e.setStatus(book.getStatus());
        e.setRating(book.getRating());
        e.setTags(book.getTags());
        e.setWikidataQid(book.getWikidataQid());
        e.setOpenLibraryKey(book.getOpenLibraryKey());
        e.setSourcePhoto(book.getSourcePhoto());
        e.setConfidence(book.getConfidence());
        e.setNotes(book.getNotes());
        e.setCreatedAt(book.getCreatedAt());
        e.setUpdatedAt(book.getUpdatedAt());
        e.setArchivedAt(book.getArchivedAt());
        return e;
    }

    /**
     * Converts a persistence entity back to the domain book.
     *
     * @param e the entity
     * @return the domain book
     */
    public static Book toDomain(BookEntity e) {
        var b = new Book();
        b.setId(e.getId());
        b.setOrganizationId(e.getOrganizationId());
        b.setTitle(e.getTitle());
        b.setSubtitle(e.getSubtitle());
        b.setEdition(e.getEdition());
        b.setAuthors(e.getAuthors());
        b.setIsbn13(e.getIsbn13());
        b.setPublisher(e.getPublisher());
        b.setYear(e.getYear());
        b.setLocation(e.getLocation());
        b.setNormalizedKey(e.getNormalizedKey());
        b.setCopies(e.getCopies());
        b.setStatus(e.getStatus());
        b.setRating(e.getRating());
        b.setTags(e.getTags());
        b.setWikidataQid(e.getWikidataQid());
        b.setOpenLibraryKey(e.getOpenLibraryKey());
        b.setSourcePhoto(e.getSourcePhoto());
        b.setConfidence(e.getConfidence());
        b.setNotes(e.getNotes());
        b.setCreatedAt(e.getCreatedAt());
        b.setUpdatedAt(e.getUpdatedAt());
        b.setArchivedAt(e.getArchivedAt());
        return b;
    }
}
