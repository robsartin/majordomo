package com.majordomo.adapter.out.persistence.herald;

import com.majordomo.domain.model.herald.CalendarToken;
import com.majordomo.domain.port.out.herald.CalendarTokenRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA-backed adapter for {@link CalendarTokenRepository}. */
@Repository
public class CalendarTokenRepositoryAdapter implements CalendarTokenRepository {

    private final JpaCalendarTokenRepository jpa;

    /**
     * Constructs the adapter.
     *
     * @param jpa Spring Data repository for {@link CalendarTokenEntity}
     */
    public CalendarTokenRepositoryAdapter(JpaCalendarTokenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public CalendarToken save(CalendarToken token) {
        return CalendarTokenMapper.toDomain(jpa.save(CalendarTokenMapper.toEntity(token)));
    }

    @Override
    public Optional<CalendarToken> findById(UUID id) {
        return jpa.findById(id).map(CalendarTokenMapper::toDomain);
    }

    @Override
    public Optional<CalendarToken> findByHashedToken(String hashedToken) {
        return jpa.findByHashedToken(hashedToken).map(CalendarTokenMapper::toDomain);
    }

    @Override
    public List<CalendarToken> findActiveByUserId(UUID userId) {
        return jpa.findByUserIdAndRevokedAtIsNull(userId).stream()
                .map(CalendarTokenMapper::toDomain).toList();
    }
}
