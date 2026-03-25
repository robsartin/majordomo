package com.majordomo.adapter.in.web.steward;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Steward domain: manages properties within organizations.
 *
 * <p>Exposes CRUD operations and hierarchy traversal under {@code /api/properties}. Acts as
 * an inbound adapter in the hexagonal architecture, delegating to
 * {@link ManagePropertyUseCase}. Properties may be nested; a property can have a parent and
 * zero or more child properties.</p>
 */
@RestController
@RequestMapping("/api/properties")
@Tag(name = "Steward", description = "Property management")
public class PropertyController {

    private final ManagePropertyUseCase propertyUseCase;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs a {@code PropertyController} with the given dependencies.
     *
     * @param propertyUseCase           the inbound port for property management
     * @param organizationAccessService the service for verifying organization membership
     */
    public PropertyController(ManagePropertyUseCase propertyUseCase,
                              OrganizationAccessService organizationAccessService) {
        this.propertyUseCase = propertyUseCase;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Returns properties belonging to the specified organization with cursor-based pagination.
     *
     * @param organizationId the UUID of the organization whose properties are retrieved
     * @param cursor         optional cursor for the next page (exclusive start)
     * @param limit          maximum number of results per page (default 20)
     * @return a page of matching properties
     */
    @GetMapping
    public Page<Property> listByOrganization(
            @RequestParam UUID organizationId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int limit) {
        organizationAccessService.verifyAccess(organizationId);
        return propertyUseCase.findByOrganizationId(organizationId, cursor, limit);
    }

    /**
     * Returns a single property by its unique identifier.
     *
     * @param id the UUID of the property to retrieve
     * @return {@code 200 OK} with the property body, or {@code 404 Not Found} if no match exists
     */
    @GetMapping("/{id}")
    public ResponseEntity<Property> getById(@PathVariable UUID id) {
        return propertyUseCase.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns the direct child properties of the specified parent property.
     *
     * @param id the UUID of the parent property
     * @return a list of child properties; empty if the property has no children
     */
    @GetMapping("/{id}/children")
    public List<Property> getChildren(@PathVariable UUID id) {
        return propertyUseCase.findByParentId(id);
    }

    /**
     * Creates a new property, delegating ID generation, default status, and timestamps
     * to the service layer.
     *
     * @param property the property data provided in the request body
     * @return {@code 201 Created} with the persisted property and a {@code Location} header
     */
    @PostMapping
    public ResponseEntity<Property> create(@Valid @RequestBody Property property) {
        organizationAccessService.verifyAccess(property.getOrganizationId());
        var saved = propertyUseCase.create(property);
        return ResponseEntity.created(URI.create("/api/properties/" + saved.getId())).body(saved);
    }

    /**
     * Updates an existing property, preserving its ID and creation timestamp.
     *
     * @param id       the UUID of the property to update
     * @param property the updated property data provided in the request body
     * @return {@code 200 OK} with the updated property
     */
    @PutMapping("/{id}")
    public ResponseEntity<Property> update(@PathVariable UUID id, @RequestBody Property property) {
        var updated = propertyUseCase.update(id, property);
        return ResponseEntity.ok(updated);
    }

    /**
     * Archives a property by setting its archived_at timestamp (soft delete).
     *
     * @param id the UUID of the property to archive
     * @return {@code 204 No Content} on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        propertyUseCase.archive(id);
        return ResponseEntity.noContent().build();
    }
}
