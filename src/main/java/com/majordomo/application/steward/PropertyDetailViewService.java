package com.majordomo.application.steward;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.concierge.Contact;
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

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembles the {@link PropertyDetailView} for the property detail page.
 * Pulls together parent + children, linked contacts + candidates, schedules
 * with due-date deltas, recent service records, and attachments — through
 * inbound ports + outbound repositories.
 *
 * <p>Belongs in the application layer rather than the controller because the
 * shape is pure orchestration over ports; controllers stay thin.</p>
 */
@Service
public class PropertyDetailViewService {

    /** Maximum recent service records exposed in the view. */
    public static final int RECENT_RECORDS_LIMIT = 10;

    private final ManagePropertyUseCase propertyUseCase;
    private final ManageScheduleUseCase scheduleUseCase;
    private final ManageAttachmentUseCase attachmentUseCase;
    private final PropertyContactRepository propertyContactRepository;
    private final ContactRepository contactRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the assembly service.
     *
     * @param propertyUseCase           inbound port for property lookups
     * @param scheduleUseCase           inbound port for schedule lookups
     * @param attachmentUseCase         inbound port for attachment listing
     * @param propertyContactRepository outbound port for property–contact rows
     * @param contactRepository         outbound port for contact reads
     * @param serviceRecordRepository   outbound port for service-record reads
     * @param organizationAccessService verifies caller has access to a given organization
     */
    public PropertyDetailViewService(ManagePropertyUseCase propertyUseCase,
                                     ManageScheduleUseCase scheduleUseCase,
                                     ManageAttachmentUseCase attachmentUseCase,
                                     PropertyContactRepository propertyContactRepository,
                                     ContactRepository contactRepository,
                                     ServiceRecordRepository serviceRecordRepository,
                                     OrganizationAccessService organizationAccessService) {
        this.propertyUseCase = propertyUseCase;
        this.scheduleUseCase = scheduleUseCase;
        this.attachmentUseCase = attachmentUseCase;
        this.propertyContactRepository = propertyContactRepository;
        this.contactRepository = contactRepository;
        this.serviceRecordRepository = serviceRecordRepository;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Builds a {@link PropertyDetailView} for the given property.
     *
     * @param propertyId the property id
     * @return the assembled view
     * @throws EntityNotFoundException if no property has that id
     * @throws org.springframework.security.access.AccessDeniedException
     *         when the caller lacks access to the property's organization
     */
    public PropertyDetailView assemble(UUID propertyId) {
        Property property = propertyUseCase.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException(
                        EntityType.PROPERTY.name(), propertyId));
        organizationAccessService.verifyAccess(property.getOrganizationId());

        Property parent = property.getParentId() == null ? null
                : propertyUseCase.findById(property.getParentId()).orElse(null);
        List<Property> children = propertyUseCase.findByParentId(propertyId);

        List<PropertyContact> activeLinks = propertyContactRepository.findByPropertyId(propertyId)
                .stream()
                .filter(pc -> pc.getArchivedAt() == null)
                .toList();
        Set<UUID> linkedContactIds = activeLinks.stream()
                .map(PropertyContact::getContactId)
                .collect(Collectors.toSet());
        Map<UUID, Contact> contactsById = contactRepository.findByIdIn(linkedContactIds).stream()
                .collect(Collectors.toMap(Contact::getId, c -> c));
        List<Contact> linkedContacts = activeLinks.stream()
                .map(pc -> contactsById.get(pc.getContactId()))
                .filter(c -> c != null)
                .toList();
        List<Contact> contactCandidates = contactRepository
                .findActiveByOrganizationIdExcluding(property.getOrganizationId(), linkedContactIds).stream()
                .sorted(Comparator.comparing(Contact::getFormattedName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        LocalDate today = LocalDate.now();
        List<PropertyDetailView.ScheduleRow> scheduleRows = new ArrayList<>();
        for (MaintenanceSchedule s : scheduleUseCase.findByPropertyId(propertyId)) {
            if (s.getArchivedAt() != null) {
                continue;
            }
            Integer days = s.getNextDue() == null ? null
                    : (int) ChronoUnit.DAYS.between(today, s.getNextDue());
            scheduleRows.add(new PropertyDetailView.ScheduleRow(s, days));
        }
        scheduleRows.sort(Comparator.comparing(
                (PropertyDetailView.ScheduleRow r) ->
                        r.daysUntilDue() == null ? Integer.MAX_VALUE : r.daysUntilDue()));

        List<ServiceRecord> recentRecords = new ArrayList<>(
                serviceRecordRepository.findByPropertyId(propertyId));
        recentRecords.removeIf(r -> r.getArchivedAt() != null);
        recentRecords.sort(Comparator.comparing(
                ServiceRecord::getPerformedOn, Comparator.nullsLast(Comparator.reverseOrder())));
        if (recentRecords.size() > RECENT_RECORDS_LIMIT) {
            recentRecords = new ArrayList<>(recentRecords.subList(0, RECENT_RECORDS_LIMIT));
        }

        List<Attachment> attachments = attachmentUseCase.list("property", propertyId);

        return new PropertyDetailView(property, parent, children,
                activeLinks, linkedContacts, contactCandidates,
                scheduleRows, recentRecords, attachments);
    }
}
