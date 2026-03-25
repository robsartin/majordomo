package com.majordomo.domain.port.out.concierge;

import com.majordomo.domain.model.concierge.Contact;

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
     * Returns all contacts belonging to a given organization.
     *
     * @param organizationId the organization whose contacts are sought
     * @return list of contacts for that organization, or an empty list if none exist
     */
    List<Contact> findByOrganizationId(UUID organizationId);
}
