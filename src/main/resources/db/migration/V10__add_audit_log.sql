CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   UUID         NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    user_id     UUID,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    diff_json   TEXT
);

CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_log_occurred_at ON audit_log(occurred_at);
