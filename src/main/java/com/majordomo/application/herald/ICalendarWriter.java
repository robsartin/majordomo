package com.majordomo.application.herald;

import com.majordomo.domain.model.herald.CalendarEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serialises {@link CalendarEvent}s into an RFC 5545 {@code VCALENDAR} document
 * suitable for a subscribable {@code .ics} feed. Events are emitted as all-day
 * {@code VEVENT}s ({@code DTSTART;VALUE=DATE}) each with a one-day-before display
 * alarm. Handles the fiddly bits of the spec: CRLF line endings, TEXT escaping,
 * and 75-octet line folding.
 */
@Component
public class ICalendarWriter {

    private static final String CRLF = "\r\n";
    private static final String PRODID = "-//Majordomo//The Herald//EN";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /**
     * Writes a VCALENDAR document.
     *
     * @param calendarName human-readable calendar name ({@code X-WR-CALNAME})
     * @param events       the events to include (may be empty)
     * @param generatedAt  timestamp used for each event's {@code DTSTAMP}
     * @return the iCalendar document as a single string
     */
    public String write(String calendarName, List<CalendarEvent> events, Instant generatedAt) {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:" + PRODID);
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "X-WR-CALNAME:" + escape(calendarName));
        String stamp = STAMP.format(generatedAt);
        for (CalendarEvent event : events) {
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:" + escape(event.uid()));
            line(sb, "DTSTAMP:" + stamp);
            line(sb, "DTSTART;VALUE=DATE:" + DATE.format(event.date()));
            line(sb, "SUMMARY:" + escape(event.summary()));
            if (event.description() != null && !event.description().isBlank()) {
                line(sb, "DESCRIPTION:" + escape(event.description()));
            }
            line(sb, "BEGIN:VALARM");
            line(sb, "ACTION:DISPLAY");
            line(sb, "DESCRIPTION:" + escape(event.summary()));
            line(sb, "TRIGGER:-P1D");
            line(sb, "END:VALARM");
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    /** Appends a content line, folded to 75 octets, terminated with CRLF. */
    private static void line(StringBuilder sb, String content) {
        sb.append(fold(content)).append(CRLF);
    }

    /**
     * Escapes a TEXT value per RFC 5545 §3.3.11: backslash, semicolon, comma,
     * and newline. Colons are not escaped in TEXT.
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /**
     * Folds a content line so no line exceeds 75 octets, inserting CRLF + space
     * at fold points (RFC 5545 §3.1). Folds on UTF-8 byte boundaries so multibyte
     * characters are never split.
     */
    private static String fold(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 75) {
            return content;
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        boolean first = true;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int charChars = Character.charCount(cp);
            int cpBytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            int limit = first ? 75 : 74; // continuation lines carry a leading space
            if (count + cpBytes > limit) {
                out.append(CRLF).append(' ');
                count = 1; // the leading space
                first = false;
            }
            out.appendCodePoint(cp);
            count += cpBytes;
            i += charChars;
        }
        return out.toString();
    }
}
