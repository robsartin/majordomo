package com.majordomo.adapter.out.persistence.envoy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.domain.model.envoy.JobPosting;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps between the {@link JobPosting} POJO and {@link JobPostingEntity},
 * serialising the {@code extracted} hint map to/from JSON.
 */
final class JobPostingMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE =
            new TypeReference<>() { };

    private JobPostingMapper() { }

    static JobPostingEntity toEntity(JobPosting p) {
        var e = new JobPostingEntity();
        e.setId(p.getId());
        e.setOrganizationId(p.getOrganizationId());
        e.setSource(p.getSource());
        e.setExternalId(p.getExternalId());
        e.setCompany(p.getCompany());
        e.setTitle(p.getTitle());
        e.setLocation(p.getLocation());
        e.setRawText(p.getRawText());
        e.setFetchedAt(p.getFetchedAt());
        e.setArchivedAt(p.getArchivedAt());
        e.setAppliedAt(p.getAppliedAt());
        e.setDismissedAt(p.getDismissedAt());
        try {
            e.setExtracted(p.getExtracted() == null ? null
                    : MAPPER.writeValueAsString(p.getExtracted()));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialise JobPosting.extracted", ex);
        }
        return e;
    }

    static JobPosting toDomain(JobPostingEntity e) {
        var p = new JobPosting();
        p.setId(e.getId());
        p.setOrganizationId(e.getOrganizationId());
        p.setSource(e.getSource());
        p.setExternalId(e.getExternalId());
        p.setCompany(e.getCompany());
        p.setTitle(e.getTitle());
        p.setLocation(e.getLocation());
        p.setRawText(e.getRawText());
        p.setFetchedAt(e.getFetchedAt());
        p.setArchivedAt(e.getArchivedAt());
        p.setAppliedAt(e.getAppliedAt());
        p.setDismissedAt(e.getDismissedAt());
        try {
            p.setExtracted(e.getExtracted() == null ? new HashMap<>()
                    : MAPPER.readValue(e.getExtracted(), MAP_TYPE));
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to deserialise JobPosting.extracted for " + e.getId(), ex);
        }
        return p;
    }
}
