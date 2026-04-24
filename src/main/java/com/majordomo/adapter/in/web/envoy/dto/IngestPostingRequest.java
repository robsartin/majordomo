package com.majordomo.adapter.in.web.envoy.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request body for {@code POST /api/envoy/postings}.
 *
 * @param type    source discriminator (e.g. "manual")
 * @param payload the posting body
 * @param hints   optional structured hints (company, title, location, externalId)
 */
public record IngestPostingRequest(
        @NotBlank String type,
        @NotBlank String payload,
        Map<String, String> hints
) { }
