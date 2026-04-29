package com.majordomo.adapter.in.web.concierge;

import java.util.ArrayList;
import java.util.List;

/**
 * Form-binding command holding the indexed {@code addresses[]} sub-form rows
 * for the contact add/edit form.
 *
 * <p>Plain top-level form fields (formattedName, emails, etc.) stay as
 * {@code @RequestParam} on the controller method — only the indexed list
 * needs a backing bean to use Spring's indexed-binding syntax.</p>
 */
public class ContactFormCommand {

    private List<AddressFormRow> addresses = new ArrayList<>();

    public List<AddressFormRow> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<AddressFormRow> addresses) {
        this.addresses = addresses != null ? addresses : new ArrayList<>();
    }
}
