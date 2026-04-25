package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.port.out.envoy.JobSource;
import com.majordomo.domain.port.out.envoy.LlmExtractionPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Ingests a job posting from an arbitrary URL by fetching the HTML and handing
 * it to {@link LlmExtractionPort} for structured-field extraction. The raw HTML
 * is preserved on the posting's {@code rawText} so the scorer can still read the
 * full body.
 */
@Component
public class UrlFetchSource implements JobSource {

    private final RestClient http;
    private final LlmExtractionPort extractor;

    /**
     * Constructs the source.
     *
     * @param http      shared HTTP client (qualifier {@code ingestRestClient})
     * @param extractor LLM-backed extraction port
     */
    public UrlFetchSource(@Qualifier("ingestRestClient") RestClient http,
                          LlmExtractionPort extractor) {
        this.http = http;
        this.extractor = extractor;
    }

    @Override
    public String name() {
        return "url";
    }

    @Override
    public boolean supports(JobSourceRequest request) {
        return "url".equals(request.type());
    }

    @Override
    public JobPosting fetch(JobSourceRequest request) {
        String url = request.payload();
        String body = http.get().uri(url).retrieve().body(String.class);
        Map<String, String> extracted = extractor.extract(body == null ? "" : body);

        var p = new JobPosting();
        p.setSource("url");
        p.setExternalId(url);
        p.setRawText(body == null ? "" : body);
        p.setCompany(extracted.get("company"));
        p.setTitle(extracted.get("title"));
        p.setLocation(extracted.get("location"));
        p.setExtracted(new HashMap<>(extracted));
        p.setFetchedAt(Instant.now());
        return p;
    }
}
