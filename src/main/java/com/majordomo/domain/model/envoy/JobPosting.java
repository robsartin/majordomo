package com.majordomo.domain.model.envoy;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A job posting ingested from a {@code JobSource}. Primary entity — gets persisted,
 * re-fetched, and potentially re-scored as rubrics evolve. Mutable to match the
 * {@code Property}/{@code Contact} convention used elsewhere in majordomo.
 *
 * <p>{@code rawText} holds the full posting body as provided by the source;
 * {@code extracted} holds any structured fields the ingester pulled out (salary,
 * equity, team size, etc.).</p>
 */
public class JobPosting {

    private UUID id;
    private UUID organizationId;
    @NotBlank
    private String source;
    private String externalId;
    private String company;
    private String title;
    private String location;
    @NotBlank
    private String rawText;
    private Map<String, String> extracted;
    private Instant fetchedAt;
    private Instant archivedAt;

    public JobPosting() { }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public Map<String, String> getExtracted() { return extracted; }
    public void setExtracted(Map<String, String> extracted) { this.extracted = extracted; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
