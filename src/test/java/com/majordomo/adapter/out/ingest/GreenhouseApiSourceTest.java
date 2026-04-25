package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GreenhouseApiSourceTest {

    private MockWebServer web;
    private GreenhouseApiSource source;

    @BeforeEach
    void setUp() throws Exception {
        web = new MockWebServer();
        web.start();
        var client = RestClient.builder().build();
        // strip trailing slash so URI template "/v1/boards/..." doesn't double up
        String base = web.url("/").toString().replaceAll("/$", "");
        source = new GreenhouseApiSource(client, base);
    }

    @AfterEach
    void tearDown() throws Exception {
        web.shutdown();
    }

    @Test
    void supportsGreenhouseType() {
        assertThat(source.supports(new JobSourceRequest("greenhouse", "1", Map.of()))).isTrue();
    }

    @Test
    void mapsGreenhousePayloadToJobPosting() {
        web.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": 12345,
                          "title": "Senior Backend Engineer",
                          "location": {"name": "Remote - US"},
                          "content": "&lt;p&gt;We are hiring&lt;/p&gt;",
                          "company_name": "Acme"
                        }
                        """));

        var req = new JobSourceRequest("greenhouse", "12345", Map.of("board", "acme"));
        JobPosting p = source.fetch(req);

        assertThat(p.getSource()).isEqualTo("greenhouse");
        assertThat(p.getExternalId()).isEqualTo("12345");
        assertThat(p.getTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(p.getLocation()).isEqualTo("Remote - US");
        assertThat(p.getCompany()).isEqualTo("Acme");
        assertThat(p.getRawText()).contains("We are hiring");
    }
}
