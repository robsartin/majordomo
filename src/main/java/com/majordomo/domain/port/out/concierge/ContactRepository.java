package com.majordomo.domain.port.out.concierge;

import com.majordomo.domain.model.concierge.Contact;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying contacts.
 * Contacts represent people or companies that an organization interacts with —
 * vendors, service providers, tenants, or other external parties.
 */
public interface ContactRepository {

    /**
     * Persists a contact, inserting or updating as needed.
     *
     * @param contact the contact to save
     * @return the saved contact, including any generated or updated fields
     */
    Contact save(Contact contact);

    /**
     * Retrieves a contact by its unique identifier.
     *
     * @param id the contact ID
     * @return the contact, or empty if not found
     */
    Optional<Contact> findById(UUID id);

    /**
     * Batch-fetches contacts by id. Used by detail handlers that need to
     * hydrate a small set of linked contacts in a single round trip.
     *
     * @param ids the contact ids to load
     * @return the matching contacts in arbitrary order, missing ids omitted
     */
    List<Contact> findByIdIn(Collection<UUID> ids);

    /**
     * Returns all contacts belonging to a given organization.
     *
     * @param organizationId the organization whose contacts are sought
     * @return list of contacts for that organization, or an empty list if none exist
     */
    List<Contact> findByOrganizationId(UUID organizationId);

    /**
     * Returns contacts for an organization with cursor-based pagination.
     *
     * @param organizationId the organization ID
     * @param cursor         exclusive start cursor (null for first page)
     * @param limit          maximum number of results
     * @return list of contacts after the cursor, ordered by ID
     */
    List<Contact> findByOrganizationId(UUID organizationId, UUID cursor, int limit);

    /**
     * Searches contacts for an organization by a case-insensitive query across
     * key text fields (formatted name, given name, family name), with cursor-based pagination.
     *
     * @param organizationId the organization whose contacts are searched
     * @param query          the search term (matched via case-insensitive LIKE)
     * @param cursor         exclusive start cursor (null for first page)
     * @param limit          maximum number of results
     * @return list of matching contacts after the cursor, ordered by ID
     */
    List<Contact> search(UUID organizationId, String query, UUID cursor, int limit);
}
