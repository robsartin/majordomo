-- Attachment text extraction for full-text search (#298, ADR-0026).
--
-- V19 indexed attachment *filenames*; this indexes what the documents say.

ALTER TABLE attachments
    ADD COLUMN extraction_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN extracted_text    TEXT,
    ADD COLUMN text_extracted_at TIMESTAMPTZ;

-- Existing attachments default to PENDING, which IS the backfill the issue
-- asks for: the scheduled sweep queues on exactly this status, so everything
-- uploaded before today is picked up by the same code path as everything
-- uploaded after. No one-off script to run once and then rot.

-- Generated, like properties.search_vector (V19), so the column cannot drift
-- from the text it summarises. The extractor caps text at 500,000 characters
-- because a tsvector over 1MB is rejected outright — which would make the row
-- impossible to write, not merely hard to search.
ALTER TABLE attachments
    ADD COLUMN content_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(extracted_text, ''))
    ) STORED;

CREATE INDEX idx_attachments_content_vector
    ON attachments USING GIN (content_vector);

-- The sweep's queue: pending, non-archived, oldest first. Partial, because the
-- rows it has to find are a shrinking minority of the table.
CREATE INDEX idx_attachments_pending_extraction
    ON attachments (created_at)
    WHERE extraction_status = 'PENDING' AND archived_at IS NULL;
