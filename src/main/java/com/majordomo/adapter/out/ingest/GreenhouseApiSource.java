package com.majordomo.adapter.out.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.port.out.envoy.JobSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Ingests a posting from the public Greenhouse job board API. Requires
 * {@code hints["board"]} (the board slug) and {@code payload} (the job id).
 */
@Component
public class GreenhouseApiSource implements JobSource {

    private final RestClient http;
    private final String baseUrl;

    /**
     * Constructs the source.
     *
     * @param http    shared HTTP client (qualifier {@code ingestRestClient})
     * @param baseUrl Greenhouse API base URL
     *                ({@code envoy.greenhouse.base-url}, default
     *                {@code https://boards-api.greenhouse.io})
     */
    public GreenhouseApiSource(@Qualifier("ingestRestClient") RestClient http,
                               @Value("${envoy.greenhouse.base-url:https://boards-api.greenhouse.io}")
                               String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    @Override
    public String name() {
        return "greenhouse";
    }

    @Override
    public boolean supports(JobSourceRequest request) {
        return "greenhouse".equals(request.type());
    }

    @Override
    public JobPosting fetch(JobSourceRequest request) {
        String board = request.hints() == null ? null : request.hints().get("board");
        if (board == null || request.payload() == null) {
            throw new IllegalArgumentException(
                    "greenhouse requires hints[\"board\"] and payload (job id)");
        }
        GhJob job = http.get()
                .uri(baseUrl + "/v1/boards/{board}/jobs/{id}", board, request.payload())
                .retrieve()
                .body(GhJob.class);
        if (job == null) {
            throw new IllegalStateException(
                    "Greenhouse returned empty body for " + request.payload());
        }

        var p = new JobPosting();
        p.setSource("greenhouse");
        p.setExternalId(String.valueOf(job.id()));
        p.setTitle(job.title());
        p.setLocation(job.location() == null ? null : job.location().name());
        p.setCompany(job.companyName());
        p.setRawText(stripHtml(job.content()));
        p.setExtracted(new HashMap<>(Map.of("board", board)));
        p.setFetchedAt(Instant.now());
        return p;
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Partial shape of Greenhouse's job-detail response.
     *
     * @param id          numeric job id
     * @param title       job title
     * @param content     HTML body
     * @param location    nested location object
     * @param companyName company name from {@code company_name}
     */
    private record GhJob(
            long id,
            String title,
            String content,
            GhLocation location,
            @JsonProperty("company_name") String companyName
    ) { }

    /**
     * Greenhouse nested location.
     *
     * @param name location display name
     */
    private record GhLocation(String name) { }
}
