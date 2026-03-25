package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.UserPreferences;
import com.majordomo.domain.port.out.identity.UserPreferencesRepository;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that fulfills the
 * {@link com.majordomo.domain.port.out.identity.UserPreferencesRepository}
 * output port by delegating to {@link JpaUserPreferencesRepository}.
 */
@Repository
public class UserPreferencesRepositoryAdapter implements UserPreferencesRepository {

    private final JpaUserPreferencesRepository jpa;

    /**
     * Constructs the adapter with the JPA repository.
     *
     * @param jpa the Spring Data JPA repository
     */
    public UserPreferencesRepositoryAdapter(JpaUserPreferencesRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public UserPreferences save(UserPreferences preferences) {
        var entity = UserPreferencesMapper.toEntity(preferences);
        return UserPreferencesMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<UserPreferences> findByUserId(UUID userId) {
        return jpa.findByUserId(userId).map(UserPreferencesMapper::toDomain);
    }
}
