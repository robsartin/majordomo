package com.majordomo.adapter.in.web.concierge;

import com.majordomo.domain.model.concierge.Contact;

import ezvcard.VCard;
import ezvcard.property.Email;
import ezvcard.property.Nickname;
import ezvcard.property.StructuredName;
import ezvcard.property.Telephone;
import ezvcard.property.Url;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Maps between Majordomo {@link Contact} domain model and ez-vcard {@link VCard} objects.
 *
 * <p>This is a stateless utility class used by {@link VCardController} to perform
 * vCard import and export conversions.</p>
 */
final class VCardMapper {

    private VCardMapper() {
        // utility class
    }

    /**
     * Converts a domain {@link Contact} to a vCard.
     *
     * @param contact the domain contact
     * @return the vCard representation
     */
    static VCard toVCard(Contact contact) {
        var vcard = new VCard();
        vcard.setFormattedName(contact.getFormattedName());

        if (contact.getFamilyName() != null || contact.getGivenName() != null) {
            var sn = new StructuredName();
            sn.setFamily(contact.getFamilyName());
            sn.setGiven(contact.getGivenName());
            vcard.setStructuredName(sn);
        }

        if (contact.getEmails() != null) {
            contact.getEmails().forEach(vcard::addEmail);
        }
        if (contact.getTelephones() != null) {
            contact.getTelephones().forEach(vcard::addTelephoneNumber);
        }
        if (contact.getUrls() != null) {
            contact.getUrls().forEach(vcard::addUrl);
        }
        if (contact.getOrganization() != null) {
            vcard.setOrganization(contact.getOrganization());
        }
        if (contact.getTitle() != null) {
            vcard.addTitle(contact.getTitle());
        }
        if (contact.getNotes() != null) {
            vcard.addNote(contact.getNotes());
        }
        if (contact.getNicknames() != null && !contact.getNicknames().isEmpty()) {
            var nickname = new Nickname();
            contact.getNicknames().forEach(nickname::addValue);
            vcard.addNickname(nickname);
        }

        return vcard;
    }

    /**
     * Converts a vCard to a domain {@link Contact}.
     *
     * <p>The returned contact has no ID or timestamps; those are assigned by the service layer
     * during creation.</p>
     *
     * @param vcard          the vCard to convert
     * @param organizationId the organization to assign the contact to
     * @return the domain contact
     */
    static Contact fromVCard(VCard vcard, UUID organizationId) {
        var contact = new Contact();
        contact.setOrganizationId(organizationId);

        if (vcard.getFormattedName() != null) {
            contact.setFormattedName(vcard.getFormattedName().getValue());
        }
        if (vcard.getStructuredName() != null) {
            contact.setFamilyName(vcard.getStructuredName().getFamily());
            contact.setGivenName(vcard.getStructuredName().getGiven());
        }

        contact.setEmails(vcard.getEmails().stream()
                .map(Email::getValue).toList());
        contact.setTelephones(vcard.getTelephoneNumbers().stream()
                .map(Telephone::getText).toList());
        contact.setUrls(vcard.getUrls().stream()
                .map(Url::getValue).toList());

        if (vcard.getOrganization() != null
                && !vcard.getOrganization().getValues().isEmpty()) {
            contact.setOrganization(vcard.getOrganization().getValues().get(0));
        }
        if (!vcard.getTitles().isEmpty()) {
            contact.setTitle(vcard.getTitles().get(0).getValue());
        }
        if (!vcard.getNotes().isEmpty()) {
            contact.setNotes(vcard.getNotes().get(0).getValue());
        }

        var nicknames = new ArrayList<String>();
        vcard.getNickname().forEach(n -> nicknames.addAll(n.getValues()));
        contact.setNicknames(nicknames);

        contact.setAddresses(List.of());
        return contact;
    }
}
