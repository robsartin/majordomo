-- The Librarian: physical book catalog with external enrichment (#315, ADR-0023).
--
-- Three tables: the catalog itself, the queue of proposed external matches that
-- stands between a lossy shelf transcription and the catalog, and the ledger of
-- what has been pushed into the interest graph.

CREATE TABLE books (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations(id),
    title            VARCHAR(512) NOT NULL,
    subtitle         VARCHAR(512),
    edition          VARCHAR(128),
    authors          TEXT[],
    isbn13           VARCHAR(20),
    publisher        VARCHAR(255),
    year             INTEGER,
    location         VARCHAR(255),
    -- Dedupe key: title and authors, normalised in Java so the importer and the
    -- lookup share one implementation rather than one here and one in SQL.
    normalized_key   VARCHAR(1024),
    copies           INTEGER,
    status           VARCHAR(32)  NOT NULL,
    rating           INTEGER,
    tags             TEXT[],
    wikidata_qid     VARCHAR(32),
    open_library_key VARCHAR(64),
    source_photo     VARCHAR(64),
    confidence       VARCHAR(16),
    notes            TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    archived_at      TIMESTAMPTZ,
    CONSTRAINT books_rating_range CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT books_copies_positive CHECK (copies IS NULL OR copies > 0)
);

-- Enforces import's upsert at the database level: one live book per normalised
-- key per org. Partial so archived rows do not block re-cataloguing a book, and
-- so rows whose key has not been computed yet do not collide with each other.
CREATE UNIQUE INDEX idx_books_org_normalized_key
    ON books(organization_id, normalized_key)
    WHERE normalized_key IS NOT NULL AND archived_at IS NULL;

CREATE INDEX idx_books_organization_id ON books(organization_id);
CREATE INDEX idx_books_wikidata_qid ON books(wikidata_qid) WHERE wikidata_qid IS NOT NULL;

-- Proposed external matches, held for review rather than applied. A rejected
-- candidate is retained rather than deleted so the decision leaves a trail and
-- a later reviewer can see what was already turned down.
CREATE TABLE book_enrichment_candidates (
    id           UUID             PRIMARY KEY,
    book_id      UUID             NOT NULL REFERENCES books(id),
    source       VARCHAR(64)      NOT NULL,
    external_id  VARCHAR(255),
    score        DOUBLE PRECISION NOT NULL,
    confidence   VARCHAR(16)      NOT NULL,
    payload      JSONB,
    retrieved_at TIMESTAMPTZ      NOT NULL,
    -- Null until a human decides. "Pending" is reviewed_at IS NULL.
    reviewed_at  TIMESTAMPTZ,
    accepted     BOOLEAN
);

CREATE INDEX idx_book_enrichment_candidates_book_id ON book_enrichment_candidates(book_id);
CREATE INDEX idx_book_enrichment_candidates_pending
    ON book_enrichment_candidates(id)
    WHERE reviewed_at IS NULL;

-- Ledger of pushes into the interest graph, which is what makes a re-sync
-- idempotent rather than duplicating nodes and edges.
CREATE TABLE book_interest_graph_links (
    id           UUID        PRIMARY KEY,
    book_id      UUID        NOT NULL REFERENCES books(id),
    wikidata_qid VARCHAR(32) NOT NULL,
    synced_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_book_interest_graph_links UNIQUE (book_id, wikidata_qid)
);

CREATE INDEX idx_book_interest_graph_links_book_id ON book_interest_graph_links(book_id);
