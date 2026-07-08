package com.majordomo.domain.model.herald;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceScheduleNextDueTest {

    private MaintenanceSchedule schedule(Frequency frequency, Integer customDays) {
        var s = new MaintenanceSchedule();
        s.setFrequency(frequency);
        s.setCustomIntervalDays(customDays);
        return s;
    }

    @Test
    void advancesByFrequencyInterval() {
        LocalDate base = LocalDate.of(2026, 7, 15);
        assertThat(schedule(Frequency.WEEKLY, null).nextDueAfter(base))
                .isEqualTo(LocalDate.of(2026, 7, 22));
        assertThat(schedule(Frequency.MONTHLY, null).nextDueAfter(base))
                .isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(schedule(Frequency.QUARTERLY, null).nextDueAfter(base))
                .isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(schedule(Frequency.SEMI_ANNUAL, null).nextDueAfter(base))
                .isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(schedule(Frequency.ANNUAL, null).nextDueAfter(base))
                .isEqualTo(LocalDate.of(2027, 7, 15));
    }

    @Test
    void customFrequencyUsesCustomIntervalDays() {
        LocalDate base = LocalDate.of(2026, 7, 15);
        assertThat(schedule(Frequency.CUSTOM, 10).nextDueAfter(base))
                .isEqualTo(LocalDate.of(2026, 7, 25));
    }

    @Test
    void customFrequencyWithoutIntervalIsRejected() {
        assertThatThrownBy(() -> schedule(Frequency.CUSTOM, null).nextDueAfter(LocalDate.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
