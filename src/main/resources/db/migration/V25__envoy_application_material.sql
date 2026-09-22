-- Generated application materials (#348, ADR-0028).
--
-- Shaped after envoy_score_report: the JSONB body is the source of truth and
-- the scalar columns exist for indexed query. The claims and their cited
-- source spans live in the body, because they are read with the draft and
-- never queried on their own.
--
-- No updated_at and no archived_at. Drafts are immutable and accumulate —
-- regenerating writes a new row, so what was actually sent stays answerable.

CREATE TABLE envoy_application_material (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    posting_id      UUID        NOT NULL REFERENCES envoy_job_posting(id),
    score_report_id UUID        REFERENCES envoy_score_report(id),
    kind            VARCHAR(32) NOT NULL,
    tone            VARCHAR(16) NOT NULL,
    body            JSONB       NOT NULL,
    llm_model       VARCHAR(64) NOT NULL,
    generated_at    TIMESTAMPTZ NOT NULL,
    input_tokens    BIGINT,
    output_tokens   BIGINT,
    latency_ms      BIGINT
);

-- Listing a posting's drafts newest-first is the one read the UI makes (#352).
CREATE INDEX envoy_application_material_posting_idx
    ON envoy_application_material (posting_id, generated_at DESC);

CREATE INDEX envoy_application_material_org_idx
    ON envoy_application_material (organization_id);
