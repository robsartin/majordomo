package com.majordomo.domain.port.out.identity;

import com.majordomo.domain.model.identity.Credential;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository {

    Credential save(Credential credential);

    Optional<Credential> findByUserId(UUID userId);
}
