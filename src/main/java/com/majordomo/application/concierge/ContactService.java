package com.majordomo.application.concierge;

import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing contact management use cases.
 * Bridges inbound ports to outbound repository ports.
 */
@Service
public class ContactService implements ManageContactUseCase {

    private final ContactRepository contactRepository;

    /**
     * Constructs the service with the contact repository port.
     *
     * @param contactRepository the outbound port for contact persistence
     */
    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Override
    public Contact create(Contact contact) {
        contact.setId(UUID.randomUUID());
        contact.setCreatedAt(Instant.now());
        contact.setUpdatedAt(Instant.now());
        return contactRepository.save(contact);
    }

    @Override
    public Optional<Contact> findById(UUID id) {
        return contactRepository.findById(id);
    }

    @Override
    public List<Contact> findByOrganizationId(UUID organizationId) {
        return contactRepository.findByOrganizationId(organizationId);
    }
}
