package com.majordomo.domain.model.envoy;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RubricValueObjectsTest {

    @Test
    void tier_exposesLabelPointsCriteria() {
        var t = new Tier("Excellent", 20, "Salary > $250k base");
        assertThat(t.label()).isEqualTo("Excellent");
        assertThat(t.points()).isEqualTo(20);
        assertThat(t.criteria()).isEqualTo("Salary > $250k base");
    }

    @Test
    void disqualifier_exposesKeyAndDescription() {
        var d = new Disqualifier("ON_SITE_ONLY", "Role requires on-site work");
        assertThat(d.key()).isEqualTo("ON_SITE_ONLY");
        assertThat(d.description()).isEqualTo("Role requires on-site work");
    }

    @Test
    void flag_exposesKeyDescriptionPenalty() {
        var f = new Flag("AT_WILL_LANGUAGE", "Aggressive at-will language", 5);
        assertThat(f.penalty()).isEqualTo(5);
    }

    @Test
    void thresholds_orderedHighToLow() {
        var th = new Thresholds(85, 70, 50);
        assertThat(th.applyImmediately()).isGreaterThan(th.apply());
        assertThat(th.apply()).isGreaterThan(th.considerOnly());
    }
}
