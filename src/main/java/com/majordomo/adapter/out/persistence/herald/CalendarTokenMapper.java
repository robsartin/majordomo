package com.majordomo.adapter.out.persistence.herald;

import com.majordomo.domain.model.herald.CalendarToken;

final class CalendarTokenMapper {

    private CalendarTokenMapper() { }

    static CalendarTokenEntity toEntity(CalendarToken token) {
        var e = new CalendarTokenEntity();
        e.setId(token.getId());
        e.setUserId(token.getUserId());
        e.setOrganizationId(token.getOrganizationId());
        e.setHashedToken(token.getHashedToken());
        e.setCreatedAt(token.getCreatedAt());
        e.setRevokedAt(token.getRevokedAt());
        return e;
    }

    static CalendarToken toDomain(CalendarTokenEntity e) {
        var token = new CalendarToken(e.getId(), e.getUserId(),
                e.getOrganizationId(), e.getHashedToken());
        token.setCreatedAt(e.getCreatedAt());
        token.setRevokedAt(e.getRevokedAt());
        return token;
    }
}
