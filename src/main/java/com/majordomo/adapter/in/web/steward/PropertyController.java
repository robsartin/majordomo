package com.majordomo.adapter.in.web.steward;

import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
public class PropertyController {

    private final ManagePropertyUseCase propertyUseCase;

    /**
     * Constructs a {@code PropertyController} with the given property use case.
     *
     * @param propertyUseCase the inbound port for property management
     */
    public PropertyController(ManagePropertyUseCase propertyUseCase) {
        this.propertyUseCase = propertyUseCase;
    }

    /**
     * Returns all properties belonging to the specified organization.
     *
     * @param organizationId the UUID of the organization whose properties are retrieved
     * @return a list of matching properties; empty if none exist
     */
    @GetMapping
    public List<Property> listByOrganization(@RequestParam UUID organizationId) {
        return propertyUseCase.findByOrganizationId(organizationId);
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
        var saved = propertyUseCase.create(property);
        return ResponseEntity.created(URI.create("/api/properties/" + saved.getId())).body(saved);
    }
}
