package com.majordomo.domain.port.out.concierge;

import com.majordomo.domain.model.concierge.Contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository {

    Contact save(Contact contact);

    Optional<Contact> findById(UUID id);

    List<Contact> findByOrganizationId(UUID organizationId);
}
