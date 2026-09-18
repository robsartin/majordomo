package com.majordomo.domain.model.librarian;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceTest {

    @Test
    @DisplayName("Confidence grades a transcribed or matched row from certain to guess")
    void shouldGradeFromHighToLowWhenEnumerated() {
        assertThat(Confidence.values())
                .containsExactly(Confidence.HIGH, Confidence.MEDIUM, Confidence.LOW);
    }
}
