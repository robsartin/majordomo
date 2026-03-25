package com.majordomo.application.steward;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.event.PropertyArchived;
import com.majordomo.domain.model.event.PropertyTransferred;
import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.majordomo.domain.model.UuidFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing property management use cases.
 * Bridges inbound ports to outbound repository ports.
 */
@Service
public class PropertyService implements ManagePropertyUseCase {

    private final PropertyRepository propertyRepository;
    private final EventPublisher eventPublisher;
    private final MembershipRepository membershipRepository;
    private final PropertyContactRepository propertyContactRepository;
    private final MaintenanceScheduleRepository maintenanceScheduleRepository;
    private final ServiceRecordRepository serviceRecordRepository;

    /**
     * Constructs the service with required outbound ports.
     *
     * @param propertyRepository            the outbound port for property persistence
     * @param eventPublisher                the outbound port for publishing domain events
     * @param membershipRepository          the outbound port for membership lookup
     * @param propertyContactRepository     the outbound port for property-contact persistence
     * @param maintenanceScheduleRepository the outbound port for maintenance schedule persistence
     * @param serviceRecordRepository       the outbound port for service record persistence
     */
    public PropertyService(PropertyRepository propertyRepository,
                           EventPublisher eventPublisher,
                           MembershipRepository membershipRepository,
                           PropertyContactRepository propertyContactRepository,
                           MaintenanceScheduleRepository maintenanceScheduleRepository,
                           ServiceRecordRepository serviceRecordRepository) {
        this.propertyRepository = propertyRepository;
        this.eventPublisher = eventPublisher;
        this.membershipRepository = membershipRepository;
        this.propertyContactRepository = propertyContactRepository;
        this.maintenanceScheduleRepository = maintenanceScheduleRepository;
        this.serviceRecordRepository = serviceRecordRepository;
    }

    @Override
    @CacheEvict(value = "properties", allEntries = true)
    public Property create(Property property) {
        property.setId(UuidFactory.newId());
        if (property.getStatus() == null) {
            property.setStatus(PropertyStatus.ACTIVE);
        }
        return propertyRepository.save(property);
    }

    @Override
    public Optional<Property> findById(UUID id) {
        return propertyRepository.findById(id);
    }

    @Override
    @Cacheable(value = "properties", key = "#organizationId")
    public List<Property> findByOrganizationId(UUID organizationId) {
        return propertyRepository.findByOrganizationId(organizationId);
    }

    @Override
    public Page<Property> findByOrganizationId(UUID organizationId, UUID cursor, int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, 100));
        var items = propertyRepository.findByOrganizationId(organizationId, cursor, clampedLimit + 1);
        return Page.fromOverfetch(items, limit, Property::getId);
    }

    @Override
    public Page<Property> search(UUID organizationId, String query, String category,
                                 String status, UUID cursor, int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, 100));
        var items = propertyRepository.search(
                organizationId, query, category, status, cursor, clampedLimit + 1);
        return Page.fromOverfetch(items, limit, Property::getId);
    }

    @Override
    public List<Property> findByParentId(UUID parentId) {
        return propertyRepository.findByParentId(parentId);
    }

    @Override
    @CacheEvict(value = "properties", allEntries = true)
    public Property update(UUID id, Property property) {
        var existing = propertyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Property", id));
        property.setId(existing.getId());
        property.setCreatedAt(existing.getCreatedAt());
        return propertyRepository.save(property);
    }

    @Override
    @CacheEvict(value = "properties", allEntries = true)
    public void archive(UUID id) {
        var existing = propertyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Property", id));
        existing.setArchivedAt(Instant.now());
        propertyRepository.save(existing);
        eventPublisher.publish(new PropertyArchived(
                existing.getId(), existing.getOrganizationId(),
                existing.getArchivedAt()));
    }

    @Override
    @CacheEvict(value = "properties", allEntries = true)
    public Property transfer(UUID propertyId, UUID toOrganizationId, UUID callerUserId) {
        var property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException("Property", propertyId));

        UUID fromOrgId = property.getOrganizationId();

        verifyCallerIsOwner(callerUserId, fromOrgId);
        verifyCallerIsOwnerOrAdmin(callerUserId, toOrganizationId);

        updateOrganization(property, toOrganizationId);
        transferChildren(propertyId, toOrganizationId);

        eventPublisher.publish(new PropertyTransferred(
                propertyId, fromOrgId, toOrganizationId, Instant.now()));

        return property;
    }

    private void verifyCallerIsOwner(UUID userId, UUID organizationId) {
        boolean isOwner = membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getOrganizationId().equals(organizationId)
                        && m.getRole() == MemberRole.OWNER);
        if (!isOwner) {
            throw new AccessDeniedException(
                    "Caller must be OWNER of source organization: " + organizationId);
        }
    }

    private void verifyCallerIsOwnerOrAdmin(UUID userId, UUID organizationId) {
        boolean hasAccess = membershipRepository.findByUserId(userId).stream()
                .anyMatch(m -> m.getOrganizationId().equals(organizationId)
                        && (m.getRole() == MemberRole.OWNER
                            || m.getRole() == MemberRole.ADMIN));
        if (!hasAccess) {
            throw new AccessDeniedException(
                    "Caller must be OWNER or ADMIN of target organization: " + organizationId);
        }
    }

    private void updateOrganization(Property property, UUID toOrganizationId) {
        property.setOrganizationId(toOrganizationId);
        property.setUpdatedAt(Instant.now());
        propertyRepository.save(property);

        for (var contact : propertyContactRepository.findByPropertyId(property.getId())) {
            contact.setUpdatedAt(Instant.now());
            propertyContactRepository.save(contact);
        }

        for (var schedule : maintenanceScheduleRepository.findByPropertyId(property.getId())) {
            schedule.setUpdatedAt(Instant.now());
            maintenanceScheduleRepository.save(schedule);
        }

        for (var record : serviceRecordRepository.findByPropertyId(property.getId())) {
            record.setUpdatedAt(Instant.now());
            serviceRecordRepository.save(record);
        }
    }

    private void transferChildren(UUID parentId, UUID toOrganizationId) {
        for (var child : propertyRepository.findByParentId(parentId)) {
            updateOrganization(child, toOrganizationId);
            transferChildren(child.getId(), toOrganizationId);
        }
    }
}
