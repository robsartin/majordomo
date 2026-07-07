package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostingContentHasherTest {

    private final PostingContentHasher hasher = new PostingContentHasher();

    private JobPosting posting(String rawText) {
        JobPosting p = new JobPosting();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(UuidFactory.newId());
        p.setCompany("Acme");
        p.setTitle("Staff Engineer");
        p.setLocation("Remote");
        p.setRawText(rawText);
        p.setExtracted(Map.of("salary", "$220k"));
        return p;
    }

    @Test
    void identicalContentProducesSameHash() {
        assertThat(hasher.hash(posting("We pay $220k")))
                .isEqualTo(hasher.hash(posting("We pay $220k")));
    }

    @Test
    void changedRawTextProducesDifferentHash() {
        assertThat(hasher.hash(posting("We pay $220k")))
                .isNotEqualTo(hasher.hash(posting("We pay $180k")));
    }

    @Test
    void hashIsIndependentOfIdentityFieldsAndDependsOnContent() {
        // Two postings with different ids but identical content hash equal;
        // changing a content field (company) changes the hash.
        JobPosting a = posting("Same body");
        JobPosting b = posting("Same body");
        assertThat(hasher.hash(a)).isEqualTo(hasher.hash(b));

        b.setCompany("Different");
        assertThat(hasher.hash(a)).isNotEqualTo(hasher.hash(b));
    }
}
