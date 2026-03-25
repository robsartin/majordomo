package com.majordomo.adapter.out.persistence.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserPreferencesEntity}, providing persistence
 * operations used by {@link UserPreferencesRepositoryAdapter}.
 */
public interface JpaUserPreferencesRepository extends JpaRepository<UserPreferencesEntity, UUID> {

    Optional<UserPreferencesEntity> findByUserId(UUID userId);
}
