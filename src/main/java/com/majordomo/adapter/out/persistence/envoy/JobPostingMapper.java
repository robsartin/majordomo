package com.majordomo.adapter.out.persistence.envoy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.majordomo.adapter.out.persistence.JsonColumnCodec;
import com.majordomo.domain.model.envoy.JobPosting;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps between the {@link JobPosting} POJO and {@link JobPostingEntity},
 * serialising the {@code extracted} hint map to/from JSON via
 * {@link JsonColumnCodec}.
 */
final class JobPostingMapper {

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
        e.setExtracted(JsonColumnCodec.encode(p.getExtracted(), "JobPosting.extracted"));
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
        Map<String, String> extracted = JsonColumnCodec.decode(
                e.getExtracted(), MAP_TYPE, "JobPosting.extracted for " + e.getId());
        p.setExtracted(extracted == null ? new HashMap<>() : extracted);
        return p;
    }
}
