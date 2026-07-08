package com.majordomo.application.herald;

import com.majordomo.domain.model.herald.CalendarEvent;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.identity.NotificationCategory;
import com.majordomo.domain.model.identity.UserPreferences;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.identity.UserPreferencesRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembles the {@link CalendarEvent}s that make up an organization's iCalendar
 * feed for a given user: upcoming maintenance due dates and property warranty
 * expirations, each gated by the user's notification-category preferences
 * ({@code MAINTENANCE_DUE} / {@code WARRANTY_EXPIRING}). Users with no saved
 * preferences see everything (notifications are opt-out).
 */
@Service
public class HeraldCalendarService {

    private final MaintenanceScheduleRepository schedules;
    private final PropertyRepository properties;
    private final UserPreferencesRepository preferences;

    /**
     * Constructs the service.
     *
     * @param schedules   maintenance schedule repository
     * @param properties  property repository
     * @param preferences user preferences repository
     */
    public HeraldCalendarService(MaintenanceScheduleRepository schedules,
                                 PropertyRepository properties,
                                 UserPreferencesRepository preferences) {
        this.schedules = schedules;
        this.properties = properties;
        this.preferences = preferences;
    }

    /**
     * Builds the calendar events for a user's organization from {@code from} onward.
     *
     * @param userId         the user the feed belongs to (drives preference filtering)
     * @param organizationId the organization whose data is published
     * @param from           inclusive lower bound (typically today)
     * @return the events to publish, honouring the user's category preferences
     */
    public List<CalendarEvent> upcomingEvents(UUID userId, UUID organizationId, LocalDate from) {
        Optional<UserPreferences> prefs = preferences.findByUserId(userId);
        List<CalendarEvent> events = new ArrayList<>();
        if (enabled(prefs, NotificationCategory.MAINTENANCE_DUE)) {
            events.addAll(maintenanceEvents(organizationId, from));
        }
        if (enabled(prefs, NotificationCategory.WARRANTY_EXPIRING)) {
            events.addAll(warrantyEvents(organizationId, from));
        }
        return events;
    }

    private List<CalendarEvent> maintenanceEvents(UUID organizationId, LocalDate from) {
        List<MaintenanceSchedule> due = schedules.findUpcomingByOrganizationId(organizationId, from);
        Map<UUID, String> names = properties.findByIdIn(
                        due.stream().map(MaintenanceSchedule::getPropertyId).toList()).stream()
                .collect(Collectors.toMap(Property::getId, Property::getName, (a, b) -> a));
        return due.stream()
                .map(s -> new CalendarEvent(
                        "maintenance-" + s.getId() + "@majordomo",
                        s.getNextDue(),
                        s.getDescription(),
                        "Property: " + names.getOrDefault(s.getPropertyId(), "unknown")))
                .toList();
    }

    private List<CalendarEvent> warrantyEvents(UUID organizationId, LocalDate from) {
        return properties.findWithWarrantyExpiringOnOrAfter(organizationId, from).stream()
                .map(p -> new CalendarEvent(
                        "warranty-" + p.getId() + "@majordomo",
                        p.getWarrantyExpiresOn(),
                        "Warranty expires: " + p.getName(),
                        p.getName()))
                .toList();
    }

    private static boolean enabled(Optional<UserPreferences> prefs, NotificationCategory category) {
        return prefs.map(p -> p.isCategoryEnabled(category)).orElse(true);
    }
}
