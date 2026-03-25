package com.majordomo.application.concierge;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.out.concierge.ContactRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServicePaginationTest {

    @Mock
    private ContactRepository contactRepository;

    private ContactService contactService;

    @BeforeEach
    void setUp() {
        contactService = new ContactService(contactRepository);
    }

    @Test
    void findByOrganizationIdFirstPageReturnsItemsAndCursor() {
        UUID orgId = UUID.randomUUID();
        // Service requests limit+1 (3) items; repo returns 3 meaning hasMore=true
        List<Contact> threeContacts = createContacts(3);
        when(contactRepository.findByOrganizationId(orgId, null, 3)).thenReturn(threeContacts);

        Page<Contact> page = contactService.findByOrganizationId(orgId, null, 2);

        assertEquals(2, page.items().size());
        assertTrue(page.hasMore());
        assertNotNull(page.nextCursor());
        assertEquals(threeContacts.get(1).getId(), page.nextCursor());
        verify(contactRepository).findByOrganizationId(orgId, null, 3);
    }

    @Test
    void findByOrganizationIdLastPageHasMoreFalse() {
        UUID orgId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        // Service requests limit+1 (3) items; repo returns only 1 meaning hasMore=false
        List<Contact> oneContact = createContacts(1);
        when(contactRepository.findByOrganizationId(orgId, cursor, 3)).thenReturn(oneContact);

        Page<Contact> page = contactService.findByOrganizationId(orgId, cursor, 2);

        assertEquals(1, page.items().size());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
        verify(contactRepository).findByOrganizationId(orgId, cursor, 3);
    }

    @Test
    void findByOrganizationIdLimitClampedMaxIs100() {
        UUID orgId = UUID.randomUUID();
        // Requesting 200 should be clamped to 100, so repo is called with 101
        when(contactRepository.findByOrganizationId(orgId, null, 101))
                .thenReturn(createContacts(101));

        Page<Contact> page = contactService.findByOrganizationId(orgId, null, 200);

        assertEquals(100, page.items().size());
        assertTrue(page.hasMore());
        verify(contactRepository).findByOrganizationId(orgId, null, 101);
    }

    @Test
    void findByOrganizationIdLimitClampedMinIs1() {
        UUID orgId = UUID.randomUUID();
        // Requesting 0 should be clamped to 1, so repo is called with 2
        when(contactRepository.findByOrganizationId(orgId, null, 2))
                .thenReturn(createContacts(1));

        Page<Contact> page = contactService.findByOrganizationId(orgId, null, 0);

        assertEquals(1, page.items().size());
        assertFalse(page.hasMore());
        verify(contactRepository).findByOrganizationId(orgId, null, 2);
    }

    @Test
    void findByOrganizationIdEmptyResultReturnsEmptyPage() {
        UUID orgId = UUID.randomUUID();
        when(contactRepository.findByOrganizationId(orgId, null, 21))
                .thenReturn(List.of());

        Page<Contact> page = contactService.findByOrganizationId(orgId, null, 20);

        assertTrue(page.items().isEmpty());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
    }

    private List<Contact> createContacts(int count) {
        List<Contact> contacts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var contact = new Contact();
            contact.setId(UUID.randomUUID());
            contact.setFormattedName("Contact " + i);
            contacts.add(contact);
        }
        return contacts;
    }
}
