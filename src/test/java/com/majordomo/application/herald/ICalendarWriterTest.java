package com.majordomo.application.herald;

import com.majordomo.domain.model.herald.CalendarEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ICalendarWriterTest {

    private final ICalendarWriter writer = new ICalendarWriter();
    private final Instant stamp = Instant.parse("2026-07-07T12:00:00Z");

    @Test
    void writesVcalendarSkeletonWithCrlfLineEndings() {
        String ics = writer.write("Majordomo", List.of(), stamp);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n");
        assertThat(ics).contains("VERSION:2.0\r\n");
        assertThat(ics).contains("PRODID:");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        // Every line break is CRLF (no bare LF).
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void writesAllDayEventWithCoreProperties() {
        var event = new CalendarEvent("sched-123@majordomo",
                LocalDate.of(2026, 7, 15), "Replace HVAC filter", "Furnace at Home");

        String ics = writer.write("Majordomo", List.of(event), stamp);

        assertThat(ics).contains("BEGIN:VEVENT\r\n");
        assertThat(ics).contains("UID:sched-123@majordomo\r\n");
        assertThat(ics).contains("DTSTAMP:20260707T120000Z\r\n");
        assertThat(ics).contains("DTSTART;VALUE=DATE:20260715\r\n");
        assertThat(ics).contains("SUMMARY:Replace HVAC filter\r\n");
        assertThat(ics).contains("DESCRIPTION:Furnace at Home\r\n");
        assertThat(ics).contains("BEGIN:VALARM\r\n");
        assertThat(ics).contains("END:VEVENT\r\n");
    }

    @Test
    void escapesTextSpecialCharacters() {
        var event = new CalendarEvent("u@majordomo", LocalDate.of(2026, 1, 1),
                "Service; clean, oil \\ inspect", "line one\nline two");

        String ics = writer.write("Majordomo", List.of(event), stamp);

        assertThat(ics).contains("SUMMARY:Service\\; clean\\, oil \\\\ inspect\r\n");
        assertThat(ics).contains("DESCRIPTION:line one\\nline two\r\n");
    }

    @Test
    void foldsLinesLongerThan75Octets() {
        String longSummary = "x".repeat(200);
        var event = new CalendarEvent("u@majordomo", LocalDate.of(2026, 1, 1), longSummary, "d");

        String ics = writer.write("Majordomo", List.of(event), stamp);

        // No content line (between CRLFs) exceeds 75 octets, and continuation
        // lines begin with a single space per RFC 5545 folding.
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .isLessThanOrEqualTo(75);
        }
        assertThat(ics).contains("\r\n ");
    }
}
