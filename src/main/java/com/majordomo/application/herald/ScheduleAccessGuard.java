package com.majordomo.application.herald;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authorization helper for Herald operations: schedules and service records are
 * scoped indirectly through their owning property, so access checks have to
 * resolve {@code propertyId → organizationId} and delegate to
 * {@link OrganizationAccessService#verifyAccess(UUID)}.
 *
 * <p>Wraps the lookups into a single dependency so {@link
 * com.majordomo.adapter.in.web.herald.ScheduleController} doesn't need to know
 * the resolution chain.</p>
 */
@Component
public class ScheduleAccessGuard {

    private final OrganizationAccessService access;
    private final PropertyRepository properties;
    private final MaintenanceScheduleRepository schedules;
    private final ServiceRecordRepository records;
    private final MembershipRepository memberships;

    /**
     * Constructs the guard.
     *
     * @param access      delegate for authenticated-user + membership checks
     * @param properties  resolves a property to its owning organization
     * @param schedules   resolves a schedule to its property
     * @param records     resolves a service record to its property
     * @param memberships resolves the authenticated user's accessible orgs
     */
    public ScheduleAccessGuard(OrganizationAccessService access,
                               PropertyRepository properties,
                               MaintenanceScheduleRepository schedules,
                               ServiceRecordRepository records,
                               MembershipRepository memberships) {
        this.access = access;
        this.properties = properties;
        this.schedules = schedules;
        this.records = records;
        this.memberships = memberships;
    }

    /**
     * Verifies the authenticated user has access to the org owning {@code propertyId}.
     *
     * @param propertyId target property id
     * @throws EntityNotFoundException if the property does not exist
     * @throws AccessDeniedException   if the user is not a member of the owning org
     */
    public void verifyForProperty(UUID propertyId) {
        UUID orgId = properties.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.PROPERTY.name(), propertyId))
                .getOrganizationId();
        access.verifyAccess(orgId);
    }

    /**
     * Verifies access for the property owning the schedule with id {@code scheduleId}.
     *
     * @param scheduleId target schedule id
     * @throws EntityNotFoundException if the schedule does not exist
     * @throws AccessDeniedException   if the user is not a member of the owning org
     */
    public void verifyForSchedule(UUID scheduleId) {
        UUID propertyId = schedules.findById(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException(
                        EntityType.MAINTENANCE_SCHEDULE.name(), scheduleId))
                .getPropertyId();
        verifyForProperty(propertyId);
    }

    /**
     * Verifies access for the property owning the service record with id {@code recordId}.
     *
     * @param recordId target service record id
     * @throws EntityNotFoundException if the record does not exist
     * @throws AccessDeniedException   if the user is not a member of the owning org
     */
    public void verifyForRecord(UUID recordId) {
        UUID propertyId = records.findById(recordId)
                .orElseThrow(() -> new EntityNotFoundException(
                        EntityType.SERVICE_RECORD.name(), recordId))
                .getPropertyId();
        verifyForProperty(propertyId);
    }

    /**
     * Returns the set of organization ids the authenticated user is a member of.
     * Used by listing endpoints (e.g. "schedules due before X") to scope the
     * result set rather than rejecting access outright.
     *
     * @return non-empty set of accessible org ids
     * @throws AccessDeniedException if the user is anonymous or has no memberships
     */
    public Set<UUID> currentUserOrganizationIds() {
        UUID userId = access.getAuthenticatedUserId();
        Set<UUID> orgs = memberships.findByUserId(userId).stream()
                .map(Membership::getOrganizationId)
                .collect(Collectors.toSet());
        if (orgs.isEmpty()) {
            throw new AccessDeniedException("User has no organization memberships");
        }
        return orgs;
    }

    /**
     * Filters a list of schedules to those whose owning property belongs to one
     * of the authenticated user's organizations. Used by the {@code /upcoming}
     * endpoint where there is no single property in scope.
     *
     * @param all   schedules to filter
     * @return filtered list (preserves order); empty if the user owns no
     *         properties matching any schedule's property
     */
    public List<com.majordomo.domain.model.herald.MaintenanceSchedule> filterToCurrentUser(
            List<com.majordomo.domain.model.herald.MaintenanceSchedule> all) {
        Set<UUID> userOrgs = currentUserOrganizationIds();
        return all.stream()
                .filter(s -> properties.findById(s.getPropertyId())
                        .map(p -> userOrgs.contains(p.getOrganizationId()))
                        .orElse(false))
                .toList();
    }
}
