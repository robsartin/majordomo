-- envoy: track APPLY_NOW conversion. Postings can be marked applied or
-- dismissed by the user; both are recorded as timestamps so the audit log
-- captures when the transition happened. Either column being non-null is
-- terminal — toggling back is not modelled in v1.
ALTER TABLE envoy_job_posting
    ADD COLUMN applied_at   TIMESTAMPTZ,
    ADD COLUMN dismissed_at TIMESTAMPTZ;

CREATE INDEX envoy_job_posting_applied_at_idx
    ON envoy_job_posting (applied_at) WHERE applied_at IS NOT NULL;
