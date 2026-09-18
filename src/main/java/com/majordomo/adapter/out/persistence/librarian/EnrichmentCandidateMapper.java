package com.majordomo.adapter.out.persistence.librarian;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;

import java.util.Map;

/**
 * Maps between {@link EnrichmentCandidate} and {@link EnrichmentCandidateEntity},
 * serialising the payload to and from the JSONB column.
 */
public final class EnrichmentCandidateMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> PAYLOAD_TYPE = new TypeReference<>() { };

    private EnrichmentCandidateMapper() {
    }

    /**
     * Converts a domain candidate to its persistence entity.
     *
     * @param candidate the domain candidate
     * @return the entity
     */
    public static EnrichmentCandidateEntity toEntity(EnrichmentCandidate candidate) {
        var e = new EnrichmentCandidateEntity();
        e.setId(candidate.id());
        e.setBookId(candidate.bookId());
        e.setSource(candidate.source());
        e.setExternalId(candidate.externalId());
        e.setScore(candidate.score());
        e.setConfidence(candidate.confidence());
        e.setPayload(writePayload(candidate.payload()));
        e.setRetrievedAt(candidate.retrievedAt());
        return e;
    }

    /**
     * Converts a persistence entity back to the domain candidate.
     *
     * @param e the entity
     * @return the domain candidate
     */
    public static EnrichmentCandidate toDomain(EnrichmentCandidateEntity e) {
        return new EnrichmentCandidate(
                e.getId(),
                e.getBookId(),
                e.getSource(),
                e.getExternalId(),
                e.getScore(),
                e.getConfidence(),
                readPayload(e.getPayload()),
                e.getRetrievedAt());
    }

    private static String writePayload(Map<String, String> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Could not serialise enrichment payload", ex);
        }
    }

    private static Map<String, String> readPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, PAYLOAD_TYPE);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            // A payload we cannot read is a real fault, not an empty payload:
            // silently returning Map.of() would turn "unreadable" into "nothing
            // was proposed" and hide it from the reviewer.
            throw new IllegalStateException("Could not read stored enrichment payload", ex);
        }
    }
}
