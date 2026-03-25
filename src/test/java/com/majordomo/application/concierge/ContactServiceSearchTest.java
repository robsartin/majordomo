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

/**
 * Unit tests for contact search functionality in {@link ContactService}.
 */
@ExtendWith(MockitoExtension.class)
class ContactServiceSearchTest {

    @Mock
    private ContactRepository contactRepository;

    private ContactService contactService;

    @BeforeEach
    void setUp() {
        contactService = new ContactService(contactRepository);
    }

    @Test
    void searchReturnsPageWithCursorWhenMoreResults() {
        UUID orgId = UUID.randomUUID();
        String query = "smith";
        List<Contact> threeContacts = createContacts(3);
        when(contactRepository.search(orgId, query, null, 3)).thenReturn(threeContacts);

        Page<Contact> page = contactService.search(orgId, query, null, 2);

        assertEquals(2, page.items().size());
        assertTrue(page.hasMore());
        assertNotNull(page.nextCursor());
        assertEquals(threeContacts.get(1).getId(), page.nextCursor());
        verify(contactRepository).search(orgId, query, null, 3);
    }

    @Test
    void searchReturnsLastPageWithNoMoreResults() {
        UUID orgId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        String query = "jones";
        List<Contact> oneContact = createContacts(1);
        when(contactRepository.search(orgId, query, cursor, 3)).thenReturn(oneContact);

        Page<Contact> page = contactService.search(orgId, query, cursor, 2);

        assertEquals(1, page.items().size());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
        verify(contactRepository).search(orgId, query, cursor, 3);
    }

    @Test
    void searchClampsLimitToMax100() {
        UUID orgId = UUID.randomUUID();
        String query = "test";
        when(contactRepository.search(orgId, query, null, 101))
                .thenReturn(createContacts(101));

        Page<Contact> page = contactService.search(orgId, query, null, 200);

        assertEquals(100, page.items().size());
        assertTrue(page.hasMore());
        verify(contactRepository).search(orgId, query, null, 101);
    }

    @Test
    void searchReturnsEmptyPageWhenNoResults() {
        UUID orgId = UUID.randomUUID();
        String query = "nonexistent";
        when(contactRepository.search(orgId, query, null, 21)).thenReturn(List.of());

        Page<Contact> page = contactService.search(orgId, query, null, 20);

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
