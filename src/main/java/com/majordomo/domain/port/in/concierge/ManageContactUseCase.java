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

    /**
     * Searches contacts for an organization by a case-insensitive query,
     * with cursor-based pagination.
     *
     * @param organizationId the organization ID
     * @param query          the search term
     * @param cursor         the cursor UUID (null for first page)
     * @param limit          max results per page (1-100)
     * @return a page of matching contacts
     */
    Page<Contact> search(UUID organizationId, String query, UUID cursor, int limit);

    /**
     * Updates an existing contact.
     *
     * @param id      the contact ID
     * @param contact the updated contact data
     * @return the updated contact
     */
    Contact update(UUID id, Contact contact);

    /**
     * Archives a contact by setting archived_at.
     *
     * @param id the contact ID
     */
    void archive(UUID id);
}
