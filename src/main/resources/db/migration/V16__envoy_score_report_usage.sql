-- Persist LLM usage data on every score report so historical reports retain
-- token counts and wall-clock latency for cost/latency analysis. All three
-- columns are nullable: existing rows keep NULL, and providers that omit
-- usage info still produce valid reports.
ALTER TABLE envoy_score_report
    ADD COLUMN input_tokens BIGINT,
    ADD COLUMN output_tokens BIGINT,
    ADD COLUMN latency_ms BIGINT;
