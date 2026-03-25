package com.majordomo.domain.model.steward;

import com.majordomo.domain.model.concierge.ContactRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Associates a {@link com.majordomo.domain.model.concierge.Contact} with a {@link Property}
 * in a specific {@link com.majordomo.domain.model.concierge.ContactRole}, such as vendor or installer.
 */
public class PropertyContact {

    private UUID id;
    private UUID propertyId;
    private UUID contactId;
    private ContactRole role;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    public PropertyContact() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPropertyId() { return propertyId; }
    public void setPropertyId(UUID propertyId) { this.propertyId = propertyId; }

    public UUID getContactId() { return contactId; }
    public void setContactId(UUID contactId) { this.contactId = contactId; }

    public ContactRole getRole() { return role; }
    public void setRole(ContactRole role) { this.role = role; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
