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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sociable unit tests for {@link WeeklyDigestService}. The collaborating
 * {@link HeraldCalendarService} is real (wired to mocked repositories) so that
 * event assembly and per-user notification-category preference filtering are
 * exercised end-to-end, while the outbound {@link NotificationPort} is mocked.
 */
@ExtendWith(MockitoExtension.class)
class WeeklyDigestServiceTest {

    @Mock
    private MaintenanceScheduleRepository scheduleRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private UserPreferencesRepository preferencesRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationPort notificationPort;

    private WeeklyDigestService service;

    @BeforeEach
    void setUp() {
        var herald = new HeraldCalendarService(scheduleRepository, propertyRepository, preferencesRepository);
        service = new WeeklyDigestService(
                herald, scheduleRepository, propertyRepository,
                membershipRepository, userRepository, notificationPort);
    }

    private MaintenanceSchedule schedule(UUID propertyId, String description, LocalDate due) {
        var s = new MaintenanceSchedule();
        s.setId(UUID.randomUUID());
        s.setPropertyId(propertyId);
        s.setDescription(description);
        s.setFrequency(Frequency.MONTHLY);
        s.setNextDue(due);
        return s;
    }

    private Property property(UUID id, UUID orgId, String name) {
        var p = new Property();
        p.setId(id);
        p.setOrganizationId(orgId);
        p.setName(name);
        return p;
    }

    @Test
    void sendsSingleDigestWithMaintenanceAndWarrantyForAdmin() {
        var orgId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var propertyId = UUID.randomUUID();

        var maintenance = schedule(propertyId, "HVAC filter replacement", LocalDate.now().plusDays(5));
        var property = property(propertyId, orgId, "Main Building");
        var warrantyProperty = property(UUID.randomUUID(), orgId, "Dishwasher");
        warrantyProperty.setWarrantyExpiresOn(LocalDate.now().plusDays(20));

        var membership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.OWNER);
        var user = new User(userId, "admin", "admin@example.com");

        // Enumeration: which organizations have anything upcoming.
        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of(maintenance));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyRepository.findWithWarrantyExpiringBefore(any(LocalDate.class)))
                .thenReturn(List.of(warrantyProperty));
        when(membershipRepository.findByOrganizationId(orgId)).thenReturn(List.of(membership));

        // Assembly (via the real HeraldCalendarService).
        when(scheduleRepository.findUpcomingByOrganizationId(eq(orgId), any(LocalDate.class)))
                .thenReturn(List.of(maintenance));
        when(propertyRepository.findByIdIn(List.of(propertyId))).thenReturn(List.of(property));
        when(propertyRepository.findWithWarrantyExpiringOnOrAfter(eq(orgId), any(LocalDate.class)))
                .thenReturn(List.of(warrantyProperty));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.sendWeeklyDigests();

        var body = ArgumentCaptor.forClass(String.class);
        verify(notificationPort).send(eq("admin@example.com"), any(String.class), body.capture());
        assertThat(body.getValue())
                .contains("HVAC filter replacement")
                .contains("Warranty expires: Dishwasher");
    }

    @Test
    void doesNotSendWhenNothingIsUpcoming() {
        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of());
        when(propertyRepository.findWithWarrantyExpiringBefore(any(LocalDate.class)))
                .thenReturn(List.of());

        service.sendWeeklyDigests();

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void doesNotSendDigestWhenUserHasOptedOutOfAllCategories() {
        var orgId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var propertyId = UUID.randomUUID();

        var maintenance = schedule(propertyId, "HVAC filter replacement", LocalDate.now().plusDays(5));
        var property = property(propertyId, orgId, "Main Building");
        var membership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.OWNER);
        var user = new User(userId, "admin", "admin@example.com");

        var prefs = new UserPreferences();
        prefs.setNotificationsEnabled(true);
        prefs.setNotificationCategoriesDisabled(List.of("MAINTENANCE_DUE", "WARRANTY_EXPIRING"));

        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of(maintenance));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyRepository.findWithWarrantyExpiringBefore(any(LocalDate.class)))
                .thenReturn(List.of());
        when(membershipRepository.findByOrganizationId(orgId)).thenReturn(List.of(membership));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));

        // Everything an enabled user would need is stubbed leniently, so the ONLY
        // reason no digest is sent is that the opt-out suppresses assembly.
        lenient().when(scheduleRepository.findUpcomingByOrganizationId(eq(orgId), any(LocalDate.class)))
                .thenReturn(List.of(maintenance));
        lenient().when(propertyRepository.findByIdIn(List.of(propertyId))).thenReturn(List.of(property));
        lenient().when(propertyRepository.findWithWarrantyExpiringOnOrAfter(eq(orgId), any(LocalDate.class)))
                .thenReturn(List.of());
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.sendWeeklyDigests();

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void excludesEventsBeyondThirtyDayWindow() {
        var orgId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var propertyId = UUID.randomUUID();

        var near = schedule(propertyId, "Gutter cleaning", LocalDate.now().plusDays(10));
        var far = schedule(propertyId, "Roof inspection", LocalDate.now().plusDays(45));
        var property = property(propertyId, orgId, "Main Building");
        var membership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.OWNER);
        var user = new User(userId, "admin", "admin@example.com");

        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of(near, far));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyRepository.findWithWarrantyExpiringBefore(any(LocalDate.class))).thenReturn(List.of());
        when(membershipRepository.findByOrganizationId(orgId)).thenReturn(List.of(membership));
        when(scheduleRepository.findUpcomingByOrganizationId(eq(orgId), any(LocalDate.class)))
                .thenReturn(List.of(near, far));
        when(propertyRepository.findByIdIn(anyCollection())).thenReturn(List.of(property));
        when(propertyRepository.findWithWarrantyExpiringOnOrAfter(eq(orgId), any(LocalDate.class)))
                .thenReturn(List.of());
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.sendWeeklyDigests();

        var body = ArgumentCaptor.forClass(String.class);
        verify(notificationPort).send(eq("admin@example.com"), anyString(), body.capture());
        assertThat(body.getValue())
                .contains("Gutter cleaning")
                .doesNotContain("Roof inspection");
    }

    @Test
    void doesNotSendDigestToPlainMembers() {
        var orgId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var propertyId = UUID.randomUUID();

        var maintenance = schedule(propertyId, "HVAC filter replacement", LocalDate.now().plusDays(5));
        var property = property(propertyId, orgId, "Main Building");
        var membership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.MEMBER);
        var user = new User(userId, "member", "member@example.com");

        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of(maintenance));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyRepository.findWithWarrantyExpiringBefore(any(LocalDate.class))).thenReturn(List.of());
        when(membershipRepository.findByOrganizationId(orgId)).thenReturn(List.of(membership));

        // Leniently stub everything a non-member would need, so the ONLY reason no
        // digest is sent is the plain-member role gate.
        lenient().when(scheduleRepository.findUpcomingByOrganizationId(eq(orgId), any(LocalDate.class)))
                .thenReturn(List.of(maintenance));
        lenient().when(propertyRepository.findByIdIn(anyCollection())).thenReturn(List.of(property));
        lenient().when(propertyRepository.findWithWarrantyExpiringOnOrAfter(eq(orgId), any(LocalDate.class)))
                .thenReturn(List.of());
        lenient().when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.sendWeeklyDigests();

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void combinesMultipleOrganizationsIntoOneDigestPerUser() {
        var orgA = UUID.randomUUID();
        var orgB = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var propertyA = UUID.randomUUID();
        var propertyB = UUID.randomUUID();

        var scheduleA = schedule(propertyA, "Furnace service", LocalDate.now().plusDays(5));
        var scheduleB = schedule(propertyB, "Chimney sweep", LocalDate.now().plusDays(7));
        var propA = property(propertyA, orgA, "Cabin");
        var propB = property(propertyB, orgB, "Townhouse");
        var user = new User(userId, "admin", "admin@example.com");

        when(scheduleRepository.findDueBefore(any(LocalDate.class)))
                .thenReturn(List.of(scheduleA, scheduleB));
        when(propertyRepository.findById(propertyA)).thenReturn(Optional.of(propA));
        when(propertyRepository.findById(propertyB)).thenReturn(Optional.of(propB));
        when(propertyRepository.findWithWarrantyExpiringBefore(any(LocalDate.class))).thenReturn(List.of());
        when(membershipRepository.findByOrganizationId(orgA))
                .thenReturn(List.of(new Membership(UUID.randomUUID(), userId, orgA, MemberRole.OWNER)));
        when(membershipRepository.findByOrganizationId(orgB))
                .thenReturn(List.of(new Membership(UUID.randomUUID(), userId, orgB, MemberRole.ADMIN)));
        when(scheduleRepository.findUpcomingByOrganizationId(eq(orgA), any(LocalDate.class)))
                .thenReturn(List.of(scheduleA));
        when(scheduleRepository.findUpcomingByOrganizationId(eq(orgB), any(LocalDate.class)))
                .thenReturn(List.of(scheduleB));
        when(propertyRepository.findByIdIn(List.of(propertyA))).thenReturn(List.of(propA));
        when(propertyRepository.findByIdIn(List.of(propertyB))).thenReturn(List.of(propB));
        when(propertyRepository.findWithWarrantyExpiringOnOrAfter(any(UUID.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.sendWeeklyDigests();

        var body = ArgumentCaptor.forClass(String.class);
        verify(notificationPort).send(eq("admin@example.com"), anyString(), body.capture());
        assertThat(body.getValue())
                .contains("Furnace service")
                .contains("Chimney sweep");
    }
}
