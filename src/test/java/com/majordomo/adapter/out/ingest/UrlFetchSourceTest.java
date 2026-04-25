package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.port.out.envoy.LlmExtractionPort;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrlFetchSourceTest {

    private MockWebServer web;
    private UrlFetchSource source;
    private LlmExtractionPort extractor;

    @BeforeEach
    void setUp() throws Exception {
        web = new MockWebServer();
        web.start();
        extractor = mock(LlmExtractionPort.class);
        source = new UrlFetchSource(RestClient.create(), extractor);
    }

    @AfterEach
    void tearDown() throws Exception {
        web.shutdown();
    }

    @Test
    void supportsUrlType() {
        assertThat(source.supports(new JobSourceRequest("url", "x", Map.of()))).isTrue();
        assertThat(source.supports(new JobSourceRequest("manual", "x", Map.of()))).isFalse();
    }

    @Test
    void fetchesAndDelegatesExtractionToLlm() {
        web.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html><body>We pay well</body></html>"));
        when(extractor.extract(any())).thenReturn(Map.of(
                "company", "Acme", "title", "Senior", "salary", "$220k"));

        var req = new JobSourceRequest("url", web.url("/job/1").toString(), Map.of());
        JobPosting p = source.fetch(req);

        assertThat(p.getRawText()).contains("We pay well");
        assertThat(p.getExtracted()).containsEntry("company", "Acme");
        assertThat(p.getSource()).isEqualTo("url");
        assertThat(p.getExternalId()).isEqualTo(web.url("/job/1").toString());
    }
}
