package com.majordomo.domain.model.librarian;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceTest {

    @Test
    void confidence_gradesHighToLow() {
        assertThat(Confidence.values())
                .containsExactly(Confidence.HIGH, Confidence.MEDIUM, Confidence.LOW);
    }
}
