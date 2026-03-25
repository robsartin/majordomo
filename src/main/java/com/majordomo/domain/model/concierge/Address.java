package com.majordomo.domain.model.concierge;

import java.util.UUID;

/**
 * A labeled postal address associated with a {@link Contact}.
 */
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
