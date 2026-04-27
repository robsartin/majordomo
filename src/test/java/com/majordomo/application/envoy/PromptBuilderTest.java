package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.Disqualifier;
import com.majordomo.domain.model.envoy.Flag;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    @Test
    void buildsSystemPromptContainingCategoryKeysAndTierLabels() {
        var rubric = new Rubric(UuidFactory.newId(), Optional.empty(), 1, "default",
                List.of(new Disqualifier("ON_SITE", "on-site required")),
                List.of(new Category("compensation", "pay", 20, List.of(
                        new Tier("Excellent", 20, ">$250k"),
                        new Tier("Good", 15, "$200-250k")))),
                List.of(new Flag("AT_WILL", "aggressive at-will", 3)),
                new Thresholds(25, 20, 10), Instant.now());

        var posting = new JobPosting();
        posting.setCompany("Acme");
        posting.setTitle("Senior Engineer");
        posting.setRawText("We offer...");

        ScoringPrompt p = new PromptBuilder().build(posting, rubric);

        assertThat(p.systemPrompt()).contains("compensation");
        assertThat(p.systemPrompt()).contains("Excellent").contains("Good");
        assertThat(p.systemPrompt()).contains("ON_SITE");
        assertThat(p.systemPrompt()).contains("AT_WILL");
        assertThat(p.systemPrompt()).doesNotContain("\"points\"");
        assertThat(p.userPrompt()).contains("Acme").contains("Senior Engineer").contains("We offer");
    }

    @Test
    void systemPromptInstructsLlmAboutConfidenceField() {
        var rubric = new Rubric(UuidFactory.newId(), Optional.empty(), 1, "default",
                List.of(),
                List.of(new Category("compensation", "pay", 20, List.of(
                        new Tier("Good", 15, "$200-250k")))),
                List.of(),
                new Thresholds(20, 15, 5), Instant.now());

        var posting = new JobPosting();
        posting.setRawText("body");

        ScoringPrompt p = new PromptBuilder().build(posting, rubric);

        assertThat(p.systemPrompt())
                .contains("confidence")
                .contains("HIGH")
                .contains("MEDIUM")
                .contains("LOW");
    }
}
