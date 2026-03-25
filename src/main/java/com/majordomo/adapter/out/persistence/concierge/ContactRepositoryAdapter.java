package com.majordomo.adapter.out.persistence.concierge;

import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.out.concierge.ContactRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
