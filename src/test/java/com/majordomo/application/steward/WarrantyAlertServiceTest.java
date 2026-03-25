package com.majordomo.application.steward;

import com.majordomo.domain.model.identity.MemberRole;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.herald.NotificationPort;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyAlertServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationPort notificationPort;

    private WarrantyAlertService service;

    @BeforeEach
    void setUp() {
        service = new WarrantyAlertService(
                propertyRepository, membershipRepository, userRepository, notificationPort);
    }

    @Test
    void checkAndNotifyDueWarrantySendsAlert() {
        var propertyId = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        var property = new Property();
        property.setId(propertyId);
        property.setOrganizationId(orgId);
        property.setName("HVAC Unit");
        property.setWarrantyExpiresOn(LocalDate.now().plusDays(15));

        var membership = new Membership(UUID.randomUUID(), userId, orgId, MemberRole.ADMIN);
        var user = new User(userId, "admin", "admin@example.com");

        when(propertyRepository.findWithWarrantyExpiringBefore(any(LocalDate.class)))
                .thenReturn(List.of(property));
        when(membershipRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(membership));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(propertyRepository.save(any(Property.class)))
                .thenReturn(property);

        service.checkAndNotify();

        verify(notificationPort).send(
                eq("admin@example.com"),
                eq("Warranty expiring soon: HVAC Unit"),
                anyString());
        verify(propertyRepository).save(property);
    }

    @Test
    void checkAndNotifyAlreadyNotifiedSkips() {
        var property = new Property();
        property.setId(UUID.randomUUID());
        property.setOrganizationId(UUID.randomUUID());
        property.setName("Old Boiler");
        property.setWarrantyExpiresOn(LocalDate.now().plusDays(5));
        property.setWarrantyNotificationSentAt(Instant.now());

        when(propertyRepository.findWithWarrantyExpiringBefore(any(LocalDate.class)))
                .thenReturn(List.of(property));

        service.checkAndNotify();

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
        verify(propertyRepository, never()).save(any());
    }

    @Test
    void checkAndNotifyNoExpiringWarrantiesDoesNothing() {
        when(propertyRepository.findWithWarrantyExpiringBefore(any(LocalDate.class)))
                .thenReturn(List.of());

        service.checkAndNotify();

        verify(notificationPort, never()).send(anyString(), anyString(), anyString());
        verify(propertyRepository, never()).save(any());
    }
}
