package com.majordomo.domain.port.in.concierge;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.concierge.Contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port for managing contacts in the Concierge domain.
 */
public interface ManageContactUseCase {

    /**
     * Creates a new contact.
     *
     * @param contact the contact to create
     * @return the created contact with generated ID and timestamps
     */
    Contact create(Contact contact);

    /**
     * Finds a contact by ID.
     *
     * @param id the contact ID
     * @return the contact, or empty if not found
     */
    Optional<Contact> findById(UUID id);

    /**
     * Lists all contacts for an organization.
     *
     * @param organizationId the organization ID
     * @return list of contacts
     */
    List<Contact> findByOrganizationId(UUID organizationId);

    /**
     * Lists contacts for an organization with cursor-based pagination.
     *
     * @param organizationId the organization ID
     * @param cursor         the cursor UUID (null for first page)
     * @param limit          max results per page (1-100)
     * @return a page of contacts
     */
    Page<Contact> findByOrganizationId(UUID organizationId, UUID cursor, int limit);
}
