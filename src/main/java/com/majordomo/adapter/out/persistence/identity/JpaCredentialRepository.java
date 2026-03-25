package com.majordomo.adapter.out.persistence.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaCredentialRepository extends JpaRepository<CredentialEntity, UUID> {

    Optional<CredentialEntity> findByUserId(UUID userId);
}
