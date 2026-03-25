package com.majordomo.adapter.in.web.concierge;

import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;

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
 * REST controller for the Concierge domain: manages contacts associated with organizations.
 *
 * <p>Exposes CRUD operations under {@code /api/contacts}. Acts as an inbound adapter in the
 * hexagonal architecture, delegating to {@link ManageContactUseCase}.</p>
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ManageContactUseCase contactUseCase;

    /**
     * Constructs a {@code ContactController} with the given contact use case.
     *
     * @param contactUseCase the inbound port for contact management
     */
    public ContactController(ManageContactUseCase contactUseCase) {
        this.contactUseCase = contactUseCase;
    }

    /**
     * Returns all contacts belonging to the specified organization.
     *
     * @param organizationId the UUID of the organization whose contacts are retrieved
     * @return a list of matching contacts; empty if none exist
     */
    @GetMapping
    public List<Contact> listByOrganization(@RequestParam UUID organizationId) {
        return contactUseCase.findByOrganizationId(organizationId);
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
        var saved = contactUseCase.create(contact);
        return ResponseEntity.created(URI.create("/api/contacts/" + saved.getId())).body(saved);
    }
}
