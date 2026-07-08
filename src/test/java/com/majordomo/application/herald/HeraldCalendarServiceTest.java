package com.majordomo.application.herald;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.herald.CalendarEvent;
import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.identity.NotificationCategory;
import com.majordomo.domain.model.identity.UserPreferences;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.identity.UserPreferencesRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeraldCalendarServiceTest {

    @Mock MaintenanceScheduleRepository schedules;
    @Mock PropertyRepository properties;
    @Mock UserPreferencesRepository preferences;

    private HeraldCalendarService service;

    private final UUID userId = UuidFactory.newId();
    private final UUID orgId = UuidFactory.newId();
    private final LocalDate from = LocalDate.of(2026, 7, 7);

    private Property furnace;
    private MaintenanceSchedule filterChange;

    @BeforeEach
    void setUp() {
        service = new HeraldCalendarService(schedules, properties, preferences);

        furnace = new Property();
        furnace.setId(UuidFactory.newId());
        furnace.setOrganizationId(orgId);
        furnace.setName("Furnace");
        furnace.setWarrantyExpiresOn(LocalDate.of(2026, 9, 1));

        filterChange = new MaintenanceSchedule();
        filterChange.setId(UuidFactory.newId());
        filterChange.setPropertyId(furnace.getId());
        filterChange.setDescription("Replace HVAC filter");
        filterChange.setFrequency(Frequency.MONTHLY);
        filterChange.setNextDue(LocalDate.of(2026, 7, 15));
    }

    @Test
    void includesUpcomingMaintenanceEvents() {
        when(preferences.findByUserId(userId)).thenReturn(Optional.empty());
        when(schedules.findUpcomingByOrganizationId(orgId, from)).thenReturn(List.of(filterChange));
        when(properties.findByIdIn(List.of(furnace.getId()))).thenReturn(List.of(furnace));
        when(properties.findWithWarrantyExpiringOnOrAfter(orgId, from)).thenReturn(List.of());

        List<CalendarEvent> events = service.upcomingEvents(userId, orgId, from);

        assertThat(events).hasSize(1);
        CalendarEvent e = events.get(0);
        assertThat(e.uid()).isEqualTo("maintenance-" + filterChange.getId() + "@majordomo");
        assertThat(e.date()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(e.summary()).isEqualTo("Replace HVAC filter");
        assertThat(e.description()).contains("Furnace");
    }

    @Test
    void includesWarrantyExpirationEvents() {
        when(preferences.findByUserId(userId)).thenReturn(Optional.empty());
        when(schedules.findUpcomingByOrganizationId(orgId, from)).thenReturn(List.of());
        when(properties.findWithWarrantyExpiringOnOrAfter(orgId, from)).thenReturn(List.of(furnace));

        List<CalendarEvent> events = service.upcomingEvents(userId, orgId, from);

        assertThat(events).hasSize(1);
        CalendarEvent e = events.get(0);
        assertThat(e.uid()).isEqualTo("warranty-" + furnace.getId() + "@majordomo");
        assertThat(e.date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(e.summary()).contains("Furnace");
    }

    @Test
    void excludesMaintenanceWhenCategoryDisabled() {
        var prefs = new UserPreferences();
        prefs.setNotificationsEnabled(true);
        prefs.setNotificationCategoriesDisabled(List.of(NotificationCategory.MAINTENANCE_DUE.name()));
        when(preferences.findByUserId(userId)).thenReturn(Optional.of(prefs));
        when(properties.findWithWarrantyExpiringOnOrAfter(orgId, from)).thenReturn(List.of());

        service.upcomingEvents(userId, orgId, from);

        // Maintenance is not even queried when the category is disabled.
        org.mockito.Mockito.verify(schedules, org.mockito.Mockito.never())
                .findUpcomingByOrganizationId(any(), any());
    }

    @Test
    void excludesWarrantyWhenCategoryDisabled() {
        var prefs = new UserPreferences();
        prefs.setNotificationsEnabled(true);
        prefs.setNotificationCategoriesDisabled(List.of(NotificationCategory.WARRANTY_EXPIRING.name()));
        when(preferences.findByUserId(userId)).thenReturn(Optional.of(prefs));
        when(schedules.findUpcomingByOrganizationId(orgId, from)).thenReturn(List.of());

        service.upcomingEvents(userId, orgId, from);

        org.mockito.Mockito.verify(properties, org.mockito.Mockito.never())
                .findWithWarrantyExpiringOnOrAfter(any(), any());
    }

    @Test
    void missingPreferencesDefaultsToAllEnabled() {
        lenient().when(preferences.findByUserId(userId)).thenReturn(Optional.empty());
        when(schedules.findUpcomingByOrganizationId(orgId, from)).thenReturn(List.of(filterChange));
        when(properties.findByIdIn(any())).thenReturn(List.of(furnace));
        when(properties.findWithWarrantyExpiringOnOrAfter(orgId, from)).thenReturn(List.of(furnace));

        List<CalendarEvent> events = service.upcomingEvents(userId, orgId, from);

        assertThat(events).hasSize(2);
    }
}
