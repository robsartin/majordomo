package com.majordomo.application.concierge;

import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.out.concierge.ContactRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository contactRepository;

    private ContactService contactService;

    @BeforeEach
    void setUp() {
        contactService = new ContactService(contactRepository);
    }

    @Test
    void createSetsIdAndTimestamps() {
        var contact = new Contact();
        contact.setFormattedName("Alice Smith");

        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = contactService.create(contact);

        assertNotNull(result.getId());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(captor.capture());
        assertNotNull(captor.getValue().getId());
        assertNotNull(captor.getValue().getCreatedAt());
        assertNotNull(captor.getValue().getUpdatedAt());
    }

    @Test
    void findByIdDelegatesToRepository() {
        UUID id = UUID.randomUUID();
        var contact = new Contact();
        contact.setId(id);
        when(contactRepository.findById(id)).thenReturn(Optional.of(contact));

        Optional<Contact> result = contactService.findById(id);

        assertEquals(Optional.of(contact), result);
        verify(contactRepository).findById(id);
    }

    @Test
    void findByOrganizationIdDelegatesToRepository() {
        UUID orgId = UUID.randomUUID();
        var contact = new Contact();
        contact.setOrganizationId(orgId);
        when(contactRepository.findByOrganizationId(orgId)).thenReturn(List.of(contact));

        List<Contact> result = contactService.findByOrganizationId(orgId);

        assertEquals(1, result.size());
        assertEquals(contact, result.get(0));
        verify(contactRepository).findByOrganizationId(orgId);
    }
}
