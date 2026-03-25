package com.majordomo.domain.port.out.steward;

import com.majordomo.domain.model.steward.PropertyContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying property-contact associations.
 * A property contact links a contact (e.g. a vendor or service provider) to a
 * specific property, optionally capturing the nature of that relationship (e.g.
 * primary plumber, emergency contact).
 */
public interface PropertyContactRepository {

    /**
     * Persists a property-contact association, inserting or updating as needed.
     *
     * @param propertyContact the association to save
     * @return the saved association, including any generated or updated fields
     */
    PropertyContact save(PropertyContact propertyContact);

    /**
     * Retrieves a property-contact association by its unique identifier.
     *
     * @param id the association ID
     * @return the association, or empty if not found
     */
    Optional<PropertyContact> findById(UUID id);

    /**
     * Returns all contacts associated with a given property.
     *
     * @param propertyId the property whose contacts are sought
     * @return list of property-contact associations for that property, or an empty list if none exist
     */
    List<PropertyContact> findByPropertyId(UUID propertyId);

    /**
     * Returns all properties associated with a given contact.
     *
     * @param contactId the contact whose property associations are sought
     * @return list of property-contact associations for that contact, or an empty list if none exist
     */
    List<PropertyContact> findByContactId(UUID contactId);
}
