CREATE TABLE attachments (
    id              UUID PRIMARY KEY,
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       UUID         NOT NULL,
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(255) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    storage_path    VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ
);

CREATE INDEX idx_attachments_entity ON attachments(entity_type, entity_id);
