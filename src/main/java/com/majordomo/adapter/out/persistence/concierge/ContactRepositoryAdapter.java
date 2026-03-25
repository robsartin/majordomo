package com.majordomo.adapter.out.persistence.concierge;

import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.out.concierge.ContactRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that fulfills the {@link com.majordomo.domain.port.out.concierge.ContactRepository}
 * output port by delegating to {@link JpaContactRepository}.
 */
@Repository
public class ContactRepositoryAdapter implements ContactRepository {

    private final JpaContactRepository jpa;

    public ContactRepositoryAdapter(JpaContactRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Contact save(Contact contact) {
        var entity = ContactMapper.toEntity(contact);
        return ContactMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Contact> findById(UUID id) {
        return jpa.findById(id).map(ContactMapper::toDomain);
    }

    @Override
    public List<Contact> findByOrganizationId(UUID organizationId) {
        return jpa.findByOrganizationId(organizationId).stream().map(ContactMapper::toDomain).toList();
    }

    @Override
    public List<Contact> findByOrganizationId(UUID organizationId, UUID cursor, int limit) {
        List<ContactEntity> entities;
        if (cursor == null) {
            entities = jpa.findByOrganizationIdOrderById(organizationId, PageRequest.of(0, limit));
        } else {
            entities = jpa.findByOrganizationIdAndIdGreaterThanOrderById(
                    organizationId, cursor, PageRequest.of(0, limit));
        }
        return entities.stream().map(ContactMapper::toDomain).toList();
    }

    @Override
    public List<Contact> search(UUID organizationId, String query, UUID cursor, int limit) {
        List<ContactEntity> entities;
        if (cursor == null) {
            entities = jpa.searchByOrganizationIdOrderById(
                    organizationId, query, PageRequest.of(0, limit));
        } else {
            entities = jpa.searchByOrganizationIdAndIdGreaterThanOrderById(
                    organizationId, query, cursor, PageRequest.of(0, limit));
        }
        return entities.stream().map(ContactMapper::toDomain).toList();
    }
}
