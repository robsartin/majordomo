package com.majordomo.application.herald;

import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.identity.UserPreferences;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.NotificationPort;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserPreferencesRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceNotificationServiceTest {

    @Mock
    private MaintenanceScheduleRepository scheduleRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferencesRepository preferencesRepository;

    @Mock
    private NotificationPort notificationPort;

    private MaintenanceNotificationService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceNotificationService(
                scheduleRepository, propertyRepository, membershipRepository,
                userRepository, preferencesRepository, notificationPort);
    }

    @Test
    void checkAndNotifyDueScheduleSendsNotification() {
        var propertyId = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        var schedule = new MaintenanceSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setPropertyId(propertyId);
        schedule.setDescription("HVAC filter replacement");
        schedule.setFrequency(Frequency.MONTHLY);
        schedule.setNextDue(LocalDate.now().plusDays(3));

        var property = new Property();
        property.setId(propertyId);
        property.setOrganizationId(orgId);
        property.setName("Main Building");

        var membership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.OWNER);

        var user = new User(userId, "admin", "admin@example.com");

        when(scheduleRepository.findDueBefore(any(LocalDate.class)))
                .thenReturn(List.of(schedule));
        when(propertyRepository.findById(propertyId))
                .thenReturn(Optional.of(property));
        when(membershipRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(membership));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(preferencesRepository.findByUserId(userId))
                .thenReturn(Optional.empty());
        when(scheduleRepository.save(any(MaintenanceSchedule.class)))
                .thenReturn(schedule);

        service.checkAndNotify();

        verify(notificationPort).send(
                eq("admin@example.com"),
                eq("Upcoming maintenance: HVAC filter replacement"),
                anyString());
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void checkAndNotifyAlreadyNotifiedSkips() {
        var schedule = new MaintenanceSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setPropertyId(UUID.randomUUID());
        schedule.setDescription("Already notified");
        schedule.setFrequency(Frequency.MONTHLY);
        schedule.setNextDue(LocalDate.now().plusDays(3));
        schedule.setNotificationSentAt(Instant.now());

        when(scheduleRepository.findDueBefore(any(LocalDate.class)))
                .thenReturn(List.of(schedule));

        service.checkAndNotify();

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void checkAndNotifyNoSchedulesDueDoesNothing() {
        when(scheduleRepository.findDueBefore(any(LocalDate.class)))
                .thenReturn(List.of());

        service.checkAndNotify();

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void checkAndNotifySkipsUserWithMaintenanceDueDisabled() {
        var propertyId = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        var schedule = new MaintenanceSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setPropertyId(propertyId);
        schedule.setDescription("HVAC filter replacement");
        schedule.setFrequency(Frequency.MONTHLY);
        schedule.setNextDue(LocalDate.now().plusDays(3));

        var property = new Property();
        property.setId(propertyId);
        property.setOrganizationId(orgId);
        property.setName("Main Building");

        var membership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.OWNER);
        var user = new User(userId, "admin", "admin@example.com");

        var prefs = new UserPreferences();
        prefs.setNotificationsEnabled(true);
        prefs.setNotificationCategoriesDisabled(List.of("MAINTENANCE_DUE"));

        when(scheduleRepository.findDueBefore(any(LocalDate.class)))
                .thenReturn(List.of(schedule));
        when(propertyRepository.findById(propertyId))
                .thenReturn(Optional.of(property));
        when(membershipRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(membership));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(preferencesRepository.findByUserId(userId))
                .thenReturn(Optional.of(prefs));
        when(scheduleRepository.save(any(MaintenanceSchedule.class)))
                .thenReturn(schedule);

        service.checkAndNotify();

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
    }
}
