package com.majordomo.adapter.in.web.steward;

import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Steward domain: manages properties within organizations.
 *
 * <p>Exposes CRUD operations and hierarchy traversal under {@code /api/properties}. Acts as
 * an inbound adapter in the hexagonal architecture, delegating persistence to
 * {@link PropertyRepository}. Properties may be nested; a property can have a parent and
 * zero or more child properties.</p>
 */
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyRepository propertyRepository;

    /**
     * Constructs a {@code PropertyController} with the given property repository.
     *
     * @param propertyRepository the port used to store and retrieve properties
     */
    public PropertyController(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /**
     * Returns all properties belonging to the specified organization.
     *
     * @param organizationId the UUID of the organization whose properties are retrieved
     * @return a list of matching properties; empty if none exist
     */
    @GetMapping
    public List<Property> listByOrganization(@RequestParam UUID organizationId) {
        return propertyRepository.findByOrganizationId(organizationId);
    }

    /**
     * Returns a single property by its unique identifier.
     *
     * @param id the UUID of the property to retrieve
     * @return {@code 200 OK} with the property body, or {@code 404 Not Found} if no match exists
     */
    @GetMapping("/{id}")
    public ResponseEntity<Property> getById(@PathVariable UUID id) {
        return propertyRepository.findById(id)
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
        return propertyRepository.findByParentId(id);
    }

    /**
     * Creates a new property, assigning a generated ID, a default status of
     * {@link PropertyStatus#ACTIVE} if none is provided, and audit timestamps.
     *
     * @param property the property data provided in the request body
     * @return {@code 201 Created} with the persisted property and a {@code Location} header
     */
    @PostMapping
    public ResponseEntity<Property> create(@RequestBody Property property) {
        property.setId(UUID.randomUUID());
        if (property.getStatus() == null) {
            property.setStatus(PropertyStatus.ACTIVE);
        }
        property.setCreatedAt(Instant.now());
        property.setUpdatedAt(Instant.now());
        var saved = propertyRepository.save(property);
        return ResponseEntity.created(URI.create("/api/properties/" + saved.getId())).body(saved);
    }
}
