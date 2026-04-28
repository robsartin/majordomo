package com.majordomo.application.herald;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ScheduleAccessGuard}.
 */
class ScheduleAccessGuardTest {

    private final OrganizationAccessService access = mock(OrganizationAccessService.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final MaintenanceScheduleRepository schedules = mock(MaintenanceScheduleRepository.class);
    private final ServiceRecordRepository records = mock(ServiceRecordRepository.class);
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final ScheduleAccessGuard guard =
            new ScheduleAccessGuard(access, properties, schedules, records, memberships);

    /** verifyForProperty resolves orgId from property and delegates to OrganizationAccessService. */
    @Test
    void verifyForPropertyDelegatesAccessCheck() {
        UUID propertyId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Property property = new Property();
        property.setId(propertyId);
        property.setOrganizationId(orgId);
        when(properties.findById(propertyId)).thenReturn(Optional.of(property));

        guard.verifyForProperty(propertyId);

        verify(access).verifyAccess(orgId);
    }

    /** verifyForProperty throws EntityNotFoundException when property doesn't exist. */
    @Test
    void verifyForPropertyThrowsWhenPropertyMissing() {
        UUID propertyId = UUID.randomUUID();
        when(properties.findById(propertyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.verifyForProperty(propertyId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    /** verifyForProperty propagates AccessDeniedException from OrganizationAccessService. */
    @Test
    void verifyForPropertyPropagatesAccessDenied() {
        UUID propertyId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Property property = new Property();
        property.setId(propertyId);
        property.setOrganizationId(orgId);
        when(properties.findById(propertyId)).thenReturn(Optional.of(property));
        doThrow(new AccessDeniedException("denied")).when(access).verifyAccess(orgId);

        assertThatThrownBy(() -> guard.verifyForProperty(propertyId))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** verifyForSchedule resolves schedule -> property -> org and delegates. */
    @Test
    void verifyForScheduleResolvesViaSchedule() {
        UUID scheduleId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setId(scheduleId);
        schedule.setPropertyId(propertyId);
        when(schedules.findById(scheduleId)).thenReturn(Optional.of(schedule));

        Property property = new Property();
        property.setId(propertyId);
        property.setOrganizationId(orgId);
        when(properties.findById(propertyId)).thenReturn(Optional.of(property));

        guard.verifyForSchedule(scheduleId);

        verify(access).verifyAccess(orgId);
    }

    /** verifyForSchedule throws when the schedule does not exist. */
    @Test
    void verifyForScheduleThrowsWhenScheduleMissing() {
        UUID scheduleId = UUID.randomUUID();
        when(schedules.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.verifyForSchedule(scheduleId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    /** verifyForRecord resolves record -> property -> org and delegates. */
    @Test
    void verifyForRecordResolvesViaRecord() {
        UUID recordId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        ServiceRecord record = new ServiceRecord();
        record.setId(recordId);
        record.setPropertyId(propertyId);
        when(records.findById(recordId)).thenReturn(Optional.of(record));

        Property property = new Property();
        property.setId(propertyId);
        property.setOrganizationId(orgId);
        when(properties.findById(propertyId)).thenReturn(Optional.of(property));

        guard.verifyForRecord(recordId);

        verify(access).verifyAccess(orgId);
    }

    /** filterToCurrentUser drops schedules whose property's org is not in the user's set. */
    @Test
    void filterToCurrentUserKeepsOnlyAccessibleSchedules() {
        UUID userId = UUID.randomUUID();
        UUID accessibleOrg = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();
        when(access.getAuthenticatedUserId()).thenReturn(userId);
        Membership membership = new Membership(UUID.randomUUID(), userId, accessibleOrg, MemberRole.OWNER);
        when(memberships.findByUserId(userId)).thenReturn(List.of(membership));

        UUID accessibleProperty = UUID.randomUUID();
        UUID otherProperty = UUID.randomUUID();
        Property accessible = new Property();
        accessible.setId(accessibleProperty);
        accessible.setOrganizationId(accessibleOrg);
        Property other = new Property();
        other.setId(otherProperty);
        other.setOrganizationId(otherOrg);
        when(properties.findById(accessibleProperty)).thenReturn(Optional.of(accessible));
        when(properties.findById(otherProperty)).thenReturn(Optional.of(other));

        MaintenanceSchedule keep = new MaintenanceSchedule();
        keep.setPropertyId(accessibleProperty);
        MaintenanceSchedule drop = new MaintenanceSchedule();
        drop.setPropertyId(otherProperty);

        var result = guard.filterToCurrentUser(List.of(keep, drop));

        assertThat(result).containsExactly(keep);
    }

    /** currentUserOrganizationIds throws AccessDeniedException for users with no memberships. */
    @Test
    void currentUserOrganizationIdsThrowsForNoMemberships() {
        UUID userId = UUID.randomUUID();
        when(access.getAuthenticatedUserId()).thenReturn(userId);
        when(memberships.findByUserId(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> guard.currentUserOrganizationIds())
                .isInstanceOf(AccessDeniedException.class);
    }
}
