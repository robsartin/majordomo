package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManualPasteSourceTest {

    private final ManualPasteSource source = new ManualPasteSource();

    @Test
    void supportsManualType() {
        assertThat(source.supports(new JobSourceRequest("manual", "x", Map.of()))).isTrue();
        assertThat(source.supports(new JobSourceRequest("url", "x", Map.of()))).isFalse();
    }

    @Test
    void fetchReturnsPostingWithRawTextAndHints() {
        var req = new JobSourceRequest("manual", "we are hiring...",
                Map.of("company", "Acme", "title", "Senior Engineer"));
        JobPosting p = source.fetch(req);

        assertThat(p.getRawText()).isEqualTo("we are hiring...");
        assertThat(p.getCompany()).isEqualTo("Acme");
        assertThat(p.getTitle()).isEqualTo("Senior Engineer");
        assertThat(p.getSource()).isEqualTo("manual");
        assertThat(p.getFetchedAt()).isNotNull();
    }
}
