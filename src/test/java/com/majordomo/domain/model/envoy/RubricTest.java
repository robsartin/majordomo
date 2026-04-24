package com.majordomo.domain.model.envoy;

import com.majordomo.domain.model.UuidFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RubricTest {

    @Test
    void rubric_holdsAllFields() {
        var tier = new Tier("Good", 15, "Salary in range");
        var cat = new Category("compensation", "Pay & equity", 20, List.of(tier));
        var rubric = new Rubric(
                UuidFactory.newId(),
                Optional.empty(),
                1,
                "default",
                List.of(new Disqualifier("ON_SITE", "on-site required")),
                List.of(cat),
                List.of(new Flag("AT_WILL", "aggressive at-will", 5)),
                new Thresholds(85, 70, 50),
                Instant.now());

        assertThat(rubric.version()).isEqualTo(1);
        assertThat(rubric.categories()).hasSize(1);
        assertThat(rubric.categories().get(0).tiers()).hasSize(1);
        assertThat(rubric.categories().get(0).maxPoints()).isEqualTo(20);
    }
}
