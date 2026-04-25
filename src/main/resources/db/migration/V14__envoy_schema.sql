-- envoy: job scoring schema. Multi-tenant by organization_id.
-- rubric.organization_id is nullable: NULL = system default template visible
-- to every org; non-null = org-specific version (shadows system default on
-- findActiveByName).
CREATE TABLE envoy_rubric (
    id UUID PRIMARY KEY,
    organization_id UUID,
    name VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL,
    body JSONB NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100)
);

-- Partial unique indexes: separate uniqueness for system defaults and per-org rubrics.
CREATE UNIQUE INDEX envoy_rubric_system_name_version_idx
    ON envoy_rubric (name, version) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX envoy_rubric_org_name_version_idx
    ON envoy_rubric (organization_id, name, version) WHERE organization_id IS NOT NULL;
CREATE INDEX envoy_rubric_name_idx ON envoy_rubric (name);
CREATE INDEX envoy_rubric_org_idx ON envoy_rubric (organization_id);

CREATE TABLE envoy_job_posting (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    source VARCHAR(50) NOT NULL,
    external_id VARCHAR(200),
    company VARCHAR(200),
    title VARCHAR(300),
    location VARCHAR(200),
    raw_text TEXT NOT NULL,
    extracted JSONB,
    fetched_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    CONSTRAINT envoy_job_posting_org_source_external_unique
        UNIQUE (organization_id, source, external_id)
);

CREATE INDEX envoy_job_posting_org_idx ON envoy_job_posting (organization_id);
CREATE INDEX envoy_job_posting_company_idx ON envoy_job_posting (company);

CREATE TABLE envoy_score_report (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    posting_id UUID NOT NULL REFERENCES envoy_job_posting(id),
    rubric_id UUID NOT NULL REFERENCES envoy_rubric(id),
    rubric_version INTEGER NOT NULL,
    body JSONB NOT NULL,
    final_score INTEGER NOT NULL,
    recommendation VARCHAR(20) NOT NULL,
    scored_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX envoy_score_report_org_idx ON envoy_score_report (organization_id);
CREATE INDEX envoy_score_report_posting_idx ON envoy_score_report (posting_id);
CREATE INDEX envoy_score_report_org_final_score_idx
    ON envoy_score_report (organization_id, final_score DESC);
CREATE INDEX envoy_score_report_org_recommendation_idx
    ON envoy_score_report (organization_id, recommendation);
