package com.majordomo.application.herald;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.herald.CalendarToken;
import com.majordomo.domain.port.out.herald.CalendarTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarTokenServiceTest {

    @Mock CalendarTokenRepository repository;

    private CalendarTokenService service;

    private final UUID userId = UuidFactory.newId();
    private final UUID orgId = UuidFactory.newId();

    @BeforeEach
    void setUp() {
        service = new CalendarTokenService(repository);
    }

    @Test
    void issueGeneratesTokenPersistsHashAndReturnsRawOnce() {
        when(repository.save(any(CalendarToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String raw = service.issue(userId, orgId);

        assertThat(raw).isNotBlank();
        ArgumentCaptor<CalendarToken> captor = ArgumentCaptor.forClass(CalendarToken.class);
        verify(repository).save(captor.capture());
        CalendarToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getOrganizationId()).isEqualTo(orgId);
        assertThat(saved.getHashedToken()).isEqualTo(CalendarTokenService.hash(raw));
        assertThat(saved.getHashedToken()).isNotEqualTo(raw);
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void resolveReturnsActiveTokenForRawValue() {
        String raw = "abc123";
        var token = new CalendarToken(UuidFactory.newId(), userId, orgId,
                CalendarTokenService.hash(raw));
        when(repository.findByHashedToken(CalendarTokenService.hash(raw)))
                .thenReturn(Optional.of(token));

        assertThat(service.resolve(raw)).contains(token);
    }

    @Test
    void resolveIgnoresRevokedToken() {
        String raw = "abc123";
        var token = new CalendarToken(UuidFactory.newId(), userId, orgId,
                CalendarTokenService.hash(raw));
        token.setRevokedAt(Instant.now());
        when(repository.findByHashedToken(CalendarTokenService.hash(raw)))
                .thenReturn(Optional.of(token));

        assertThat(service.resolve(raw)).isEmpty();
    }

    @Test
    void resolveReturnsEmptyForUnknownToken() {
        when(repository.findByHashedToken(any())).thenReturn(Optional.empty());

        assertThat(service.resolve("nope")).isEmpty();
    }

    @Test
    void revokeSetsRevokedAtWhenOwnedByUser() {
        var token = new CalendarToken(UuidFactory.newId(), userId, orgId, "h");
        when(repository.findById(token.getId())).thenReturn(Optional.of(token));
        when(repository.save(any(CalendarToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.revoke(token.getId(), userId);

        ArgumentCaptor<CalendarToken> captor = ArgumentCaptor.forClass(CalendarToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRevokedAt()).isNotNull();
    }

    @Test
    void revokeIgnoresTokenOwnedByAnotherUser() {
        var token = new CalendarToken(UuidFactory.newId(), UuidFactory.newId(), orgId, "h");
        when(repository.findById(token.getId())).thenReturn(Optional.of(token));

        service.revoke(token.getId(), userId);

        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
