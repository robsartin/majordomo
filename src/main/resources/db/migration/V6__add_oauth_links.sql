CREATE TABLE oauth_links (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id),
    provider        VARCHAR(50)  NOT NULL,
    external_id     VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ,
    UNIQUE (provider, external_id)
);

CREATE INDEX idx_oauth_links_user_id ON oauth_links(user_id);
CREATE INDEX idx_oauth_links_provider_external_id ON oauth_links(provider, external_id);
