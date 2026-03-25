package com.majordomo.adapter.out.persistence.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CredentialEntity}, providing persistence operations
 * used by {@link CredentialRepositoryAdapter}.
 */
public interface JpaCredentialRepository extends JpaRepository<CredentialEntity, UUID> {

    Optional<CredentialEntity> findByUserId(UUID userId);
}
