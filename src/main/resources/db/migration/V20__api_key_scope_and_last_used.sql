-- Scoped API keys + usage tracking (#293).
-- scope constrains what a key may do (READ_ONLY vs READ_WRITE); existing keys
-- are backfilled to READ_WRITE so their behaviour is unchanged. last_used_at is
-- stamped by the auth filter on each authenticated request.

ALTER TABLE api_keys
    ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'READ_WRITE';

ALTER TABLE api_keys
    ADD COLUMN last_used_at TIMESTAMPTZ;
