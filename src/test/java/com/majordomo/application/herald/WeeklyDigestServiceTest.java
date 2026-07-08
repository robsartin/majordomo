package com.majordomo.application.herald;

import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
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
import static org.mockito.ArgumentMatchers.eq;
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
}
