package com.majordomo.adapter.in.web;

import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.AuditLogRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for querying audit log entries.
 *
 * <p>Provides endpoints to query audit history by entity and to retrieve
 * an organization-wide activity feed across all properties.</p>
 */
@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit", description = "Activity audit log queries")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final PropertyRepository propertyRepository;

    /**
     * Constructs the controller with required repositories.
     *
     * @param auditLogRepository the outbound port for audit log queries
     * @param propertyRepository the outbound port for property queries
     */
    public AuditController(AuditLogRepository auditLogRepository,
                           PropertyRepository propertyRepository) {
        this.auditLogRepository = auditLogRepository;
        this.propertyRepository = propertyRepository;
    }

    /**
     * Queries audit log entries by entity type and entity ID.
     *
     * @param entityType the entity type (e.g. "ServiceRecord", "Property")
     * @param entityId   the entity ID
     * @return list of audit log entries for the specified entity
     */
    @GetMapping
    public ResponseEntity<List<AuditLogEntry>> queryByEntity(
            @RequestParam String entityType,
            @RequestParam UUID entityId) {
        var entries = auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId);
        return ResponseEntity.ok(entries);
    }

    /**
     * Returns an organization-wide activity feed by aggregating audit entries
     * for all properties belonging to the organization.
     *
     * @param orgId the organization UUID
     * @return list of audit log entries across all organization properties,
     *         sorted by occurred_at descending
     */
    @GetMapping("/organizations/{orgId}")
    public ResponseEntity<List<AuditLogEntry>> organizationFeed(@PathVariable UUID orgId) {
        List<Property> properties = propertyRepository.findByOrganizationId(orgId);
        List<AuditLogEntry> allEntries = new ArrayList<>();
        for (Property property : properties) {
            allEntries.addAll(
                    auditLogRepository.findByEntityTypeAndEntityId("Property", property.getId()));
        }
        allEntries.sort(Comparator.comparing(AuditLogEntry::getOccurredAt).reversed());
        return ResponseEntity.ok(allEntries);
    }
}
