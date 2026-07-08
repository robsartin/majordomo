package com.majordomo.domain.model.herald;

import java.time.LocalDate;

/**
 * A single all-day calendar entry to publish in an iCalendar feed — for example
 * a maintenance task due date or a warranty expiration. Pure data; the mapping
 * from schedules/properties and the iCalendar serialisation live elsewhere.
 *
 * @param uid         stable, globally-unique identifier for the entry (so calendar
 *                    clients update rather than duplicate it across refreshes)
 * @param date        the all-day date the entry falls on
 * @param summary     short title shown in the calendar
 * @param description longer detail (e.g. the property the entry relates to)
 */
public record CalendarEvent(String uid, LocalDate date, String summary, String description) {
}
