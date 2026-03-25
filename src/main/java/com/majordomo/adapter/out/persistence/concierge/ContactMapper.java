package com.majordomo.adapter.out.persistence.concierge;

import com.majordomo.domain.model.concierge.Address;
import com.majordomo.domain.model.concierge.Contact;

import java.util.List;

final class ContactMapper {

    private ContactMapper() {}

    static ContactEntity toEntity(Contact contact) {
        var entity = new ContactEntity();
        entity.setId(contact.getId());
        entity.setOrganizationId(contact.getOrganizationId());
        entity.setFormattedName(contact.getFormattedName());
        entity.setFamilyName(contact.getFamilyName());
        entity.setGivenName(contact.getGivenName());
        entity.setNicknames(contact.getNicknames());
        entity.setEmails(contact.getEmails());
        entity.setTelephones(contact.getTelephones());
        entity.setUrls(contact.getUrls());
        entity.setOrganization(contact.getOrganization());
        entity.setTitle(contact.getTitle());
        entity.setNotes(contact.getNotes());
        entity.setCreatedAt(contact.getCreatedAt());
        entity.setUpdatedAt(contact.getUpdatedAt());
        entity.setArchivedAt(contact.getArchivedAt());
        return entity;
    }

    static Contact toDomain(ContactEntity entity) {
        var contact = new Contact();
        contact.setId(entity.getId());
        contact.setOrganizationId(entity.getOrganizationId());
        contact.setFormattedName(entity.getFormattedName());
        contact.setFamilyName(entity.getFamilyName());
        contact.setGivenName(entity.getGivenName());
        contact.setNicknames(entity.getNicknames());
        contact.setEmails(entity.getEmails());
        contact.setTelephones(entity.getTelephones());
        contact.setUrls(entity.getUrls());
        contact.setOrganization(entity.getOrganization());
        contact.setTitle(entity.getTitle());
        contact.setNotes(entity.getNotes());
        contact.setCreatedAt(entity.getCreatedAt());
        contact.setUpdatedAt(entity.getUpdatedAt());
        contact.setArchivedAt(entity.getArchivedAt());
        if (entity.getAddresses() != null) {
            contact.setAddresses(entity.getAddresses().stream().map(ContactMapper::toDomain).toList());
        } else {
            contact.setAddresses(List.of());
        }
        return contact;
    }

    static Address toDomain(AddressEntity entity) {
        return new Address(
            entity.getId(),
            entity.getContactId(),
            entity.getLabel(),
            entity.getStreet(),
            entity.getCity(),
            entity.getState(),
            entity.getPostalCode(),
            entity.getCountry()
        );
    }
}
