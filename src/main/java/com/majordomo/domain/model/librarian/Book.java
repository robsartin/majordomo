package com.majordomo.domain.model.librarian;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A book in the physical library.
 *
 * <p>Carries three kinds of field side by side: what the owner recorded
 * (title, location, rating, status), what enrichment resolved against external
 * catalogs (ISBN, publisher, Wikidata QID, Open Library key), and the
 * provenance of the first two ({@code sourcePhoto}, {@code confidence}).
 * Keeping provenance on the aggregate is what lets the review queue tell a
 * cleanly-read spine from an author filled in from memory.
 *
 * <p>Mutable POJO with getters and setters, following the {@code Property} and
 * {@code Contact} convention rather than the record style used by Envoy's
 * value objects.
 */
public class Book {

    private UUID id;
    private UUID organizationId;
    @NotBlank
    private String title;
    private String subtitle;
    private String edition;
    private List<String> authors;
    private String isbn13;
    private String publisher;
    private Integer year;
    private String location;
    private Integer copies;
    private BookStatus status;
    @Min(1)
    @Max(5)
    private Integer rating;
    private List<String> tags;
    private String wikidataQid;
    private String openLibraryKey;
    private String sourcePhoto;
    private Confidence confidence;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    public Book() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getEdition() { return edition; }
    public void setEdition(String edition) { this.edition = edition; }

    public List<String> getAuthors() { return authors; }
    public void setAuthors(List<String> authors) { this.authors = authors; }

    public String getIsbn13() { return isbn13; }
    public void setIsbn13(String isbn13) { this.isbn13 = isbn13; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getCopies() { return copies; }
    public void setCopies(Integer copies) { this.copies = copies; }

    public BookStatus getStatus() { return status; }
    public void setStatus(BookStatus status) { this.status = status; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getWikidataQid() { return wikidataQid; }
    public void setWikidataQid(String wikidataQid) { this.wikidataQid = wikidataQid; }

    public String getOpenLibraryKey() { return openLibraryKey; }
    public void setOpenLibraryKey(String openLibraryKey) { this.openLibraryKey = openLibraryKey; }

    public String getSourcePhoto() { return sourcePhoto; }
    public void setSourcePhoto(String sourcePhoto) { this.sourcePhoto = sourcePhoto; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
