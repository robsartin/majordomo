package com.majordomo.domain.port.out.envoy;

import java.util.Map;

/**
 * Outbound port for LLM-driven structured extraction from free text (typically HTML).
 * Callers pass raw posting text; the port returns a flat map of labelled fields
 * (company, title, location, salary, etc.).
 */
public interface LlmExtractionPort {

    /**
     * Extracts structured fields from a posting's raw body. Keys in the returned map
     * are a best-effort subset of: {@code company}, {@code title}, {@code location},
     * {@code salary}, {@code equity}, {@code team}, {@code tech}.
     *
     * @param rawText raw body of a job posting (HTML or plain text)
     * @return a flat map of extracted fields
     */
    Map<String, String> extract(String rawText);
}
