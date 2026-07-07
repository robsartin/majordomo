-- Full-text search for The Steward (#291).
-- A generated tsvector over the property's own text fields, plus GIN indexes
-- on it and on attachment filenames, so property search can use ranked/stemmed
-- Postgres FTS instead of case-insensitive LIKE. Attachment *content* extraction
-- is a separate effort (#298); only filenames are indexed here.

ALTER TABLE properties
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('english',
            coalesce(name, '')          || ' ' ||
            coalesce(description, '')    || ' ' ||
            coalesce(serial_number, '')  || ' ' ||
            coalesce(model_number, '')   || ' ' ||
            coalesce(manufacturer, '')   || ' ' ||
            coalesce(category, '')       || ' ' ||
            coalesce(location, '')
        )
    ) STORED;

CREATE INDEX idx_properties_search_vector
    ON properties USING GIN (search_vector);

-- Filenames like "furnace-installation-manual.pdf" tokenise as file/host tokens
-- under the default parser, so normalise non-alphanumerics to spaces first (in
-- both the index and the query) to index plain words like "manual".
CREATE INDEX idx_attachments_filename_fts
    ON attachments USING GIN (
        to_tsvector('english', regexp_replace(filename, '[^A-Za-z0-9]+', ' ', 'g'))
    );
