-- Per-user tokens that authenticate the public iCalendar feed (#286).
-- The raw token lives only in the feed URL; only its SHA-256 hash is stored.
-- Soft-revoked via revoked_at.

CREATE TABLE calendar_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id),
    organization_id UUID         NOT NULL REFERENCES organizations(id),
    hashed_token    VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_calendar_tokens_hashed_token ON calendar_tokens(hashed_token);
CREATE INDEX idx_calendar_tokens_user_id ON calendar_tokens(user_id);
