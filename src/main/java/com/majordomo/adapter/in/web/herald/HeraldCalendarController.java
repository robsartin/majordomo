package com.majordomo.adapter.in.web.herald;

import com.majordomo.application.herald.CalendarTokenService;
import com.majordomo.application.herald.HeraldCalendarService;
import com.majordomo.application.herald.ICalendarWriter;
import com.majordomo.domain.model.herald.CalendarToken;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Publishes an organization's upcoming maintenance + warranty events as a
 * subscribable iCalendar feed, authenticated solely by an unguessable token in
 * the URL (no session). The path is {@code /herald/calendar/{token}.ics} and is
 * permitted anonymously in {@link com.majordomo.adapter.in.web.config.SecurityConfig};
 * an unknown or revoked token yields {@code 404} so token validity is not
 * revealed.
 */
@RestController
public class HeraldCalendarController {

    private static final String CALENDAR_NAME = "Majordomo — Upcoming";

    private final CalendarTokenService tokens;
    private final HeraldCalendarService calendar;
    private final ICalendarWriter writer;

    /**
     * Constructs the controller.
     *
     * @param tokens   calendar token service (resolve token → owner)
     * @param calendar event assembly service
     * @param writer   iCalendar serialiser
     */
    public HeraldCalendarController(CalendarTokenService tokens,
                                    HeraldCalendarService calendar,
                                    ICalendarWriter writer) {
        this.tokens = tokens;
        this.calendar = calendar;
        this.writer = writer;
    }

    /**
     * Returns the iCalendar feed for the token's owner/organization.
     *
     * @param token the raw feed token from the URL
     * @return {@code 200} with a {@code text/calendar} body, or {@code 404} if the
     *         token is unknown or revoked
     */
    @GetMapping(value = "/herald/calendar/{token}.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> feed(@PathVariable String token) {
        Optional<CalendarToken> resolved = tokens.resolve(token);
        if (resolved.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CalendarToken owner = resolved.get();
        var events = calendar.upcomingEvents(
                owner.getUserId(), owner.getOrganizationId(), LocalDate.now());
        String ics = writer.write(CALENDAR_NAME, events, Instant.now());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"majordomo.ics\"")
                .body(ics);
    }
}
