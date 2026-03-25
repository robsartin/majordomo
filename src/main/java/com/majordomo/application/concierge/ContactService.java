package com.majordomo.application.concierge;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
    @CacheEvict(value = "contacts", allEntries = true)
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
    @Cacheable(value = "contacts", key = "#organizationId")
    public List<Contact> findByOrganizationId(UUID organizationId) {
        return contactRepository.findByOrganizationId(organizationId);
    }

    @Override
    public Page<Contact> findByOrganizationId(UUID organizationId, UUID cursor, int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, 100));
        var items = contactRepository.findByOrganizationId(organizationId, cursor, clampedLimit + 1);
        return Page.fromOverfetch(items, limit, Contact::getId);
    }

    @Override
    public Page<Contact> search(UUID organizationId, String query, UUID cursor, int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, 100));
        var items = contactRepository.search(organizationId, query, cursor, clampedLimit + 1);
        return Page.fromOverfetch(items, limit, Contact::getId);
    }

    @Override
    @CacheEvict(value = "contacts", allEntries = true)
    public Contact update(UUID id, Contact contact) {
        var existing = contactRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Contact", id));
        contact.setId(existing.getId());
        contact.setCreatedAt(existing.getCreatedAt());
        contact.setUpdatedAt(Instant.now());
        return contactRepository.save(contact);
    }

    @Override
    @CacheEvict(value = "contacts", allEntries = true)
    public void archive(UUID id) {
        var existing = contactRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Contact", id));
        existing.setArchivedAt(Instant.now());
        contactRepository.save(existing);
    }
}
