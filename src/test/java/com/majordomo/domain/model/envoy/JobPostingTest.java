package com.majordomo.domain.model.envoy;

import com.majordomo.domain.model.UuidFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingTest {

    @Test
    void jobPosting_roundTripsAllFields() {
        var p = new JobPosting();
        p.setId(UuidFactory.newId());
        p.setSource("manual");
        p.setExternalId("abc-123");
        p.setCompany("Acme");
        p.setTitle("Senior Engineer");
        p.setLocation("Remote (US)");
        p.setRawText("We are hiring...");
        p.setExtracted(Map.of("salary", "$200k-$250k"));
        p.setFetchedAt(Instant.parse("2026-04-23T12:00:00Z"));

        assertThat(p.getCompany()).isEqualTo("Acme");
        assertThat(p.getExtracted()).containsEntry("salary", "$200k-$250k");
        assertThat(p.getFetchedAt()).isEqualTo(Instant.parse("2026-04-23T12:00:00Z"));
    }

    @Test
    void jobSourceRequest_holdsTypePayloadHints() {
        var req = new JobSourceRequest("url", "https://jobs.example.com/1", Map.of("company", "Acme"));
        assertThat(req.type()).isEqualTo("url");
        assertThat(req.hints()).containsEntry("company", "Acme");
    }
}
