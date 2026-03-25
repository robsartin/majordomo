package com.majordomo.adapter.in.web.concierge;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;

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
import java.util.UUID;

/**
 * REST controller for the Concierge domain: manages contacts associated with organizations.
 *
 * <p>Exposes CRUD operations under {@code /api/contacts}. Acts as an inbound adapter in the
 * hexagonal architecture, delegating to {@link ManageContactUseCase}.</p>
 */
@RestController
@RequestMapping("/api/contacts")
@Tag(name = "Concierge", description = "Contact management")
public class ContactController {

    private final ManageContactUseCase contactUseCase;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs a {@code ContactController} with the given dependencies.
     *
     * @param contactUseCase            the inbound port for contact management
     * @param organizationAccessService the service for verifying organization membership
     */
    public ContactController(ManageContactUseCase contactUseCase,
                             OrganizationAccessService organizationAccessService) {
        this.contactUseCase = contactUseCase;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Returns contacts belonging to the specified organization with cursor-based pagination.
     * When a search query is provided via {@code q}, results are filtered by a case-insensitive
     * match across key text fields.
     *
     * @param organizationId the UUID of the organization whose contacts are retrieved
     * @param q              optional search query for case-insensitive filtering
     * @param cursor         optional cursor for the next page (exclusive start)
     * @param limit          maximum number of results per page (default 20)
     * @return a page of matching contacts
     */
    @GetMapping
    public Page<Contact> listByOrganization(
            @RequestParam UUID organizationId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int limit) {
        organizationAccessService.verifyAccess(organizationId);
        if (q != null && !q.isBlank()) {
            return contactUseCase.search(organizationId, q, cursor, limit);
        }
        return contactUseCase.findByOrganizationId(organizationId, cursor, limit);
    }

    /**
     * Returns a single contact by its unique identifier.
     *
     * @param id the UUID of the contact to retrieve
     * @return {@code 200 OK} with the contact body, or {@code 404 Not Found} if no match exists
     */
    @GetMapping("/{id}")
    public ResponseEntity<Contact> getById(@PathVariable UUID id) {
        return contactUseCase.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new contact, delegating ID generation and timestamps to the service layer.
     *
     * @param contact the contact data provided in the request body
     * @return {@code 201 Created} with the persisted contact and a {@code Location} header
     */
    @PostMapping
    public ResponseEntity<Contact> create(@Valid @RequestBody Contact contact) {
        organizationAccessService.verifyAccess(contact.getOrganizationId());
        var saved = contactUseCase.create(contact);
        return ResponseEntity.created(URI.create("/api/contacts/" + saved.getId())).body(saved);
    }

    /**
     * Updates an existing contact, preserving its ID and creation timestamp.
     *
     * @param id      the UUID of the contact to update
     * @param contact the updated contact data provided in the request body
     * @return {@code 200 OK} with the updated contact
     */
    @PutMapping("/{id}")
    public ResponseEntity<Contact> update(@PathVariable UUID id, @RequestBody Contact contact) {
        var updated = contactUseCase.update(id, contact);
        return ResponseEntity.ok(updated);
    }

    /**
     * Archives a contact by setting its archived_at timestamp (soft delete).
     *
     * @param id the UUID of the contact to archive
     * @return {@code 204 No Content} on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        contactUseCase.archive(id);
        return ResponseEntity.noContent().build();
    }
}
