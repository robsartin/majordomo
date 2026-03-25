package com.majordomo.adapter.in.web.concierge;

import com.majordomo.domain.model.concierge.Contact;

import ezvcard.VCard;
import ezvcard.property.Nickname;
import ezvcard.property.StructuredName;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link VCardMapper}.
 */
class VCardMapperTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    /**
     * Verifies that all domain Contact fields are mapped to the vCard representation.
     */
    @Test
    void toVCardMapsAllFields() {
        var contact = buildSampleContact();

        var vcard = VCardMapper.toVCard(contact);

        assertEquals("Jane Doe", vcard.getFormattedName().getValue());
        assertEquals("Doe", vcard.getStructuredName().getFamily());
        assertEquals("Jane", vcard.getStructuredName().getGiven());
        assertEquals(1, vcard.getEmails().size());
        assertEquals("jane@example.com", vcard.getEmails().get(0).getValue());
        assertEquals(1, vcard.getTelephoneNumbers().size());
        assertEquals("+1-555-0100", vcard.getTelephoneNumbers().get(0).getText());
        assertEquals(1, vcard.getUrls().size());
        assertEquals("https://example.com", vcard.getUrls().get(0).getValue());
        assertEquals("Acme Corp", vcard.getOrganization().getValues().get(0));
        assertEquals("Engineer", vcard.getTitles().get(0).getValue());
        assertEquals("A note", vcard.getNotes().get(0).getValue());
        assertEquals(1, vcard.getNicknames().size());
        assertEquals(List.of("JD"), vcard.getNicknames().get(0).getValues());
    }

    /**
     * Verifies that all vCard fields are mapped to a domain Contact.
     */
    @Test
    void fromVCardMapsAllFields() {
        var vcard = new VCard();
        vcard.setFormattedName("Jane Doe");
        var sn = new StructuredName();
        sn.setFamily("Doe");
        sn.setGiven("Jane");
        vcard.setStructuredName(sn);
        vcard.addEmail("jane@example.com");
        vcard.addTelephoneNumber("+1-555-0100");
        vcard.addUrl("https://example.com");
        vcard.setOrganization("Acme Corp");
        vcard.addTitle("Engineer");
        vcard.addNote("A note");
        var nickname = new Nickname();
        nickname.getValues().add("JD");
        vcard.addNickname(nickname);

        var contact = VCardMapper.fromVCard(vcard, ORG_ID);

        assertEquals(ORG_ID, contact.getOrganizationId());
        assertEquals("Jane Doe", contact.getFormattedName());
        assertEquals("Doe", contact.getFamilyName());
        assertEquals("Jane", contact.getGivenName());
        assertEquals(List.of("jane@example.com"), contact.getEmails());
        assertEquals(List.of("+1-555-0100"), contact.getTelephones());
        assertEquals(List.of("https://example.com"), contact.getUrls());
        assertEquals("Acme Corp", contact.getOrganization());
        assertEquals("Engineer", contact.getTitle());
        assertEquals("A note", contact.getNotes());
        assertEquals(List.of("JD"), contact.getNicknames());
        assertNotNull(contact.getAddresses());
    }

    /**
     * Verifies that converting a Contact to vCard and back preserves data.
     */
    @Test
    void roundTripPreservesData() {
        var original = buildSampleContact();

        var vcard = VCardMapper.toVCard(original);
        var result = VCardMapper.fromVCard(vcard, ORG_ID);

        assertEquals(original.getFormattedName(), result.getFormattedName());
        assertEquals(original.getFamilyName(), result.getFamilyName());
        assertEquals(original.getGivenName(), result.getGivenName());
        assertEquals(original.getEmails(), result.getEmails());
        assertEquals(original.getTelephones(), result.getTelephones());
        assertEquals(original.getUrls(), result.getUrls());
        assertEquals(original.getOrganization(), result.getOrganization());
        assertEquals(original.getTitle(), result.getTitle());
        assertEquals(original.getNotes(), result.getNotes());
        assertEquals(original.getNicknames(), result.getNicknames());
    }

    private Contact buildSampleContact() {
        var contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setOrganizationId(ORG_ID);
        contact.setFormattedName("Jane Doe");
        contact.setFamilyName("Doe");
        contact.setGivenName("Jane");
        contact.setNicknames(List.of("JD"));
        contact.setEmails(List.of("jane@example.com"));
        contact.setTelephones(List.of("+1-555-0100"));
        contact.setUrls(List.of("https://example.com"));
        contact.setOrganization("Acme Corp");
        contact.setTitle("Engineer");
        contact.setNotes("A note");
        contact.setAddresses(List.of());
        return contact;
    }
}
