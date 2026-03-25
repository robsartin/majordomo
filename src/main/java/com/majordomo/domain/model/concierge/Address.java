package com.majordomo.domain.model.concierge;

import java.util.UUID;

public record Address(
    UUID id,
    UUID contactId,
    String label,
    String street,
    String city,
    String state,
    String postalCode,
    String country
) {}
