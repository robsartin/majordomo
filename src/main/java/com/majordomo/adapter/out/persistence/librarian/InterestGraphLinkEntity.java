package com.majordomo.adapter.out.persistence.librarian;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "book_interest_graph_links")
public class InterestGraphLinkEntity {

    @Id
    private UUID id;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(name = "wikidata_qid", nullable = false)
    private String wikidataQid;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBookId() { return bookId; }
    public void setBookId(UUID bookId) { this.bookId = bookId; }

    public String getWikidataQid() { return wikidataQid; }
    public void setWikidataQid(String wikidataQid) { this.wikidataQid = wikidataQid; }

    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
}
