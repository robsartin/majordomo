ALTER TABLE audit_log
    ADD COLUMN organization_id UUID NULL;

CREATE INDEX idx_audit_log_organization_id ON audit_log(organization_id);
CREATE INDEX idx_audit_log_org_occurred_at ON audit_log(organization_id, occurred_at DESC);
