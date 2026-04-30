package com.majordomo.application.steward;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyContact;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for {@link PropertyDetailViewService} — no Spring context. */
class PropertyDetailViewServiceTest {

    private ManagePropertyUseCase propertyUseCase;
    private ManageScheduleUseCase scheduleUseCase;
    private ManageAttachmentUseCase attachmentUseCase;
    private PropertyContactRepository propertyContactRepository;
    private ContactRepository contactRepository;
    private ServiceRecordRepository serviceRecordRepository;
    private OrganizationAccessService organizationAccessService;

    private PropertyDetailViewService service;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void setUp() {
        propertyUseCase = mock(ManagePropertyUseCase.class);
        scheduleUseCase = mock(ManageScheduleUseCase.class);
        attachmentUseCase = mock(ManageAttachmentUseCase.class);
        propertyContactRepository = mock(PropertyContactRepository.class);
        contactRepository = mock(ContactRepository.class);
        serviceRecordRepository = mock(ServiceRecordRepository.class);
        organizationAccessService = mock(OrganizationAccessService.class);
        service = new PropertyDetailViewService(propertyUseCase, scheduleUseCase,
                attachmentUseCase, propertyContactRepository, contactRepository,
                serviceRecordRepository, organizationAccessService);
    }

    /** Cycle 1: assemble a happy-path detail view with all panels populated. */
    @Test
    void assemblesHappyPathView() {
        UUID propertyId = UuidFactory.newId();
        UUID parentId = UuidFactory.newId();
        UUID childId = UuidFactory.newId();
        UUID contactId = UuidFactory.newId();
        UUID otherContactId = UuidFactory.newId();
        UUID scheduleId = UuidFactory.newId();
        UUID serviceRecordId = UuidFactory.newId();

        Property property = property(propertyId, "Beach House", parentId);
        Property parent = property(parentId, "Estate", null);
        Property child = property(childId, "Boiler", propertyId);

        when(propertyUseCase.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyUseCase.findById(parentId)).thenReturn(Optional.of(parent));
        when(propertyUseCase.findByParentId(propertyId)).thenReturn(List.of(child));

        PropertyContact link = new PropertyContact();
        link.setId(UuidFactory.newId());
        link.setPropertyId(propertyId);
        link.setContactId(contactId);
        when(propertyContactRepository.findByPropertyId(propertyId)).thenReturn(List.of(link));

        Contact linkedContact = contact(contactId, "Linked Larry");
        Contact candidateContact = contact(otherContactId, "Candidate Carol");
        when(contactRepository.findByIdIn(any())).thenReturn(List.of(linkedContact));
        when(contactRepository.findActiveByOrganizationIdExcluding(ORG_ID, java.util.Set.of(contactId)))
                .thenReturn(List.of(candidateContact));

        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setId(scheduleId);
        schedule.setPropertyId(propertyId);
        schedule.setDescription("Annual HVAC");
        schedule.setFrequency(Frequency.ANNUAL);
        schedule.setNextDue(LocalDate.now().plusDays(10));
        when(scheduleUseCase.findByPropertyId(propertyId)).thenReturn(List.of(schedule));

        ServiceRecord record = new ServiceRecord();
        record.setId(serviceRecordId);
        record.setPropertyId(propertyId);
        record.setPerformedOn(LocalDate.now().minusDays(5));
        when(serviceRecordRepository.findByPropertyId(propertyId)).thenReturn(List.of(record));

        Attachment attachment = mock(Attachment.class);
        when(attachmentUseCase.list("property", propertyId)).thenReturn(List.of(attachment));

        PropertyDetailView view = service.assemble(propertyId);

        assertThat(view.property()).isSameAs(property);
        assertThat(view.parent()).isSameAs(parent);
        assertThat(view.children()).containsExactly(child);
        assertThat(view.linkedContacts()).containsExactly(linkedContact);
        assertThat(view.propertyContacts()).containsExactly(link);
        assertThat(view.contactCandidates()).containsExactly(candidateContact);
        assertThat(view.scheduleRows()).hasSize(1);
        assertThat(view.scheduleRows().get(0).schedule()).isSameAs(schedule);
        assertThat(view.scheduleRows().get(0).daysUntilDue()).isEqualTo(10);
        assertThat(view.recentRecords()).containsExactly(record);
        assertThat(view.attachments()).containsExactly(attachment);
    }

    /** Cycle 2: missing property bubbles EntityNotFoundException (mapped to 404 by the controller advice). */
    @Test
    void throwsWhenPropertyMissing() {
        UUID id = UuidFactory.newId();
        when(propertyUseCase.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assemble(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    /** Cycle 3: cross-org access propagates from OrganizationAccessService. */
    @Test
    void throwsWhenCrossOrg() {
        UUID id = UuidFactory.newId();
        UUID otherOrg = UuidFactory.newId();
        Property foreign = property(id, "Cross-org", null);
        foreign.setOrganizationId(otherOrg);
        when(propertyUseCase.findById(id)).thenReturn(Optional.of(foreign));
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(otherOrg);

        assertThatThrownBy(() -> service.assemble(id))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Cycle 4: archived schedules and records are filtered out; recent-records cap honored. */
    @Test
    void filtersArchivedAndCapsRecentRecords() {
        UUID id = UuidFactory.newId();
        Property property = property(id, "P", null);
        when(propertyUseCase.findById(id)).thenReturn(Optional.of(property));
        when(propertyContactRepository.findByPropertyId(id)).thenReturn(List.of());
        when(contactRepository.findByIdIn(any())).thenReturn(List.of());
        when(contactRepository.findActiveByOrganizationIdExcluding(any(), any()))
                .thenReturn(List.of());

        MaintenanceSchedule active = new MaintenanceSchedule();
        active.setId(UuidFactory.newId());
        active.setPropertyId(id);
        active.setNextDue(LocalDate.now());
        MaintenanceSchedule archived = new MaintenanceSchedule();
        archived.setId(UuidFactory.newId());
        archived.setPropertyId(id);
        archived.setArchivedAt(Instant.now());
        when(scheduleUseCase.findByPropertyId(id)).thenReturn(List.of(active, archived));

        java.util.List<ServiceRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            ServiceRecord r = new ServiceRecord();
            r.setId(UuidFactory.newId());
            r.setPropertyId(id);
            r.setPerformedOn(LocalDate.now().minusDays(i));
            records.add(r);
        }
        // One archived record should also be filtered.
        ServiceRecord archivedRecord = new ServiceRecord();
        archivedRecord.setId(UuidFactory.newId());
        archivedRecord.setPropertyId(id);
        archivedRecord.setArchivedAt(Instant.now());
        records.add(archivedRecord);
        when(serviceRecordRepository.findByPropertyId(id)).thenReturn(records);
        when(attachmentUseCase.list("property", id)).thenReturn(List.of());

        PropertyDetailView view = service.assemble(id);

        assertThat(view.scheduleRows()).hasSize(1);
        assertThat(view.recentRecords()).hasSize(10); // capped
        assertThat(view.recentRecords()).noneMatch(r -> r.getArchivedAt() != null);
    }

    private static Property property(UUID id, String name, UUID parentId) {
        Property p = new Property();
        p.setId(id);
        p.setOrganizationId(ORG_ID);
        p.setName(name);
        p.setParentId(parentId);
        return p;
    }

    private static Contact contact(UUID id, String name) {
        Contact c = new Contact();
        c.setId(id);
        c.setOrganizationId(ORG_ID);
        c.setFormattedName(name);
        return c;
    }
}
