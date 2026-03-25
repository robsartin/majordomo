package com.majordomo.application.steward;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.event.PropertyTransferred;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyContact;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the property transfer use case in {@link PropertyService}.
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceTransferTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private PropertyContactRepository propertyContactRepository;

    @Mock
    private MaintenanceScheduleRepository maintenanceScheduleRepository;

    @Mock
    private ServiceRecordRepository serviceRecordRepository;

    private PropertyService propertyService;

    private UUID propertyId;
    private UUID fromOrgId;
    private UUID toOrgId;
    private UUID callerUserId;

    /** Sets up the service under test with mocked dependencies. */
    @BeforeEach
    void setUp() {
        propertyService = new PropertyService(propertyRepository, eventPublisher,
                membershipRepository, propertyContactRepository,
                maintenanceScheduleRepository, serviceRecordRepository);

        propertyId = UUID.randomUUID();
        fromOrgId = UUID.randomUUID();
        toOrgId = UUID.randomUUID();
        callerUserId = UUID.randomUUID();
    }

    /** Verifies a successful transfer updates the property and publishes an event. */
    @Test
    void transferSuccessfullyUpdatesPropertyAndPublishesEvent() {
        var property = buildProperty(propertyId, fromOrgId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubOwnerOf(callerUserId, fromOrgId);
        stubAdminOf(callerUserId, toOrgId);
        when(propertyRepository.findByParentId(propertyId)).thenReturn(List.of());
        when(propertyContactRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(maintenanceScheduleRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(serviceRecordRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        Property result = propertyService.transfer(propertyId, toOrgId, callerUserId);

        assertEquals(toOrgId, result.getOrganizationId());
        assertNotNull(result.getUpdatedAt());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(captor.capture());
        var event = assertInstanceOf(PropertyTransferred.class, captor.getValue());
        assertEquals(propertyId, event.propertyId());
        assertEquals(fromOrgId, event.fromOrgId());
        assertEquals(toOrgId, event.toOrgId());
        assertNotNull(event.occurredAt());
    }

    /** Verifies that transfer also updates child properties recursively. */
    @Test
    void transferUpdatesChildPropertiesRecursively() {
        var property = buildProperty(propertyId, fromOrgId);
        UUID childId = UUID.randomUUID();
        var child = buildProperty(childId, fromOrgId);

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubOwnerOf(callerUserId, fromOrgId);
        stubAdminOf(callerUserId, toOrgId);

        when(propertyRepository.findByParentId(propertyId)).thenReturn(List.of(child));
        when(propertyRepository.findByParentId(childId)).thenReturn(List.of());
        when(propertyContactRepository.findByPropertyId(any())).thenReturn(List.of());
        when(maintenanceScheduleRepository.findByPropertyId(any())).thenReturn(List.of());
        when(serviceRecordRepository.findByPropertyId(any())).thenReturn(List.of());

        propertyService.transfer(propertyId, toOrgId, callerUserId);

        assertEquals(toOrgId, child.getOrganizationId());
    }

    /** Verifies that transfer updates associated contacts, schedules, and service records. */
    @Test
    void transferUpdatesAssociatedEntities() {
        var property = buildProperty(propertyId, fromOrgId);
        var contact = new PropertyContact();
        contact.setId(UUID.randomUUID());
        var schedule = new MaintenanceSchedule();
        schedule.setId(UUID.randomUUID());
        var record = new ServiceRecord();
        record.setId(UUID.randomUUID());

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubOwnerOf(callerUserId, fromOrgId);
        stubAdminOf(callerUserId, toOrgId);

        when(propertyRepository.findByParentId(propertyId)).thenReturn(List.of());
        when(propertyContactRepository.findByPropertyId(propertyId)).thenReturn(List.of(contact));
        when(maintenanceScheduleRepository.findByPropertyId(propertyId))
                .thenReturn(List.of(schedule));
        when(serviceRecordRepository.findByPropertyId(propertyId)).thenReturn(List.of(record));
        when(propertyContactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(maintenanceScheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        propertyService.transfer(propertyId, toOrgId, callerUserId);

        verify(propertyContactRepository).save(contact);
        verify(maintenanceScheduleRepository).save(schedule);
        verify(serviceRecordRepository).save(record);
    }

    /** Verifies that a caller who is not OWNER of the source org gets a 403. */
    @Test
    void transferThrowsAccessDeniedWhenCallerNotOwnerOfSource() {
        var property = buildProperty(propertyId, fromOrgId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        stubMemberOf(callerUserId, fromOrgId);

        assertThrows(AccessDeniedException.class,
                () -> propertyService.transfer(propertyId, toOrgId, callerUserId));

        verify(eventPublisher, never()).publish(any());
    }

    /** Verifies that a caller who is not OWNER or ADMIN of the target org gets a 403. */
    @Test
    void transferThrowsAccessDeniedWhenCallerNotMemberOfTarget() {
        var property = buildProperty(propertyId, fromOrgId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        stubOwnerOf(callerUserId, fromOrgId);
        // No membership in target org
        when(membershipRepository.findByUserId(callerUserId))
                .thenReturn(List.of(new Membership(UUID.randomUUID(), callerUserId,
                        fromOrgId, MemberRole.OWNER)));

        assertThrows(AccessDeniedException.class,
                () -> propertyService.transfer(propertyId, toOrgId, callerUserId));

        verify(eventPublisher, never()).publish(any());
    }

    /** Verifies that transferring a non-existent property throws 404. */
    @Test
    void transferThrowsEntityNotFoundWhenPropertyMissing() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> propertyService.transfer(propertyId, toOrgId, callerUserId));

        verify(eventPublisher, never()).publish(any());
    }

    private Property buildProperty(UUID id, UUID orgId) {
        var property = new Property();
        property.setId(id);
        property.setOrganizationId(orgId);
        property.setName("Test Property");
        return property;
    }

    private void stubOwnerOf(UUID userId, UUID orgId) {
        var ownerMembership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.OWNER);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(ownerMembership));
    }

    private void stubAdminOf(UUID userId, UUID orgId) {
        var ownerMembership = new Membership(UUID.randomUUID(), userId, fromOrgId, MemberRole.OWNER);
        var adminMembership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.ADMIN);
        when(membershipRepository.findByUserId(userId))
                .thenReturn(List.of(ownerMembership, adminMembership));
    }

    private void stubMemberOf(UUID userId, UUID orgId) {
        var memberMembership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.MEMBER);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(memberMembership));
    }
}
