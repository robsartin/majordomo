package com.majordomo.adapter.out.ingest;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.port.out.envoy.JobSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Ingests raw pasted posting text. {@code request.payload()} holds the body;
 * {@code request.hints()} optionally supplies {@code company}, {@code title},
 * {@code location}, and {@code externalId}.
 */
@Component
public class ManualPasteSource implements JobSource {

    @Override
    public String name() {
        return "manual";
    }

    @Override
    public boolean supports(JobSourceRequest request) {
        return "manual".equals(request.type());
    }

    @Override
    public JobPosting fetch(JobSourceRequest request) {
        var p = new JobPosting();
        p.setSource("manual");
        p.setRawText(request.payload());
        p.setFetchedAt(Instant.now());
        Map<String, String> hints = request.hints() == null ? Map.of() : request.hints();
        p.setCompany(hints.get("company"));
        p.setTitle(hints.get("title"));
        p.setLocation(hints.get("location"));
        p.setExternalId(hints.get("externalId"));
        p.setExtracted(new HashMap<>(hints));
        return p;
    }
}
