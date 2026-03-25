package com.majordomo.application.steward;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private PropertyContactRepository propertyContactRepository;

    @Mock
    private MaintenanceScheduleRepository maintenanceScheduleRepository;

    @Mock
    private ServiceRecordRepository serviceRecordRepository;

    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        propertyService = new PropertyService(propertyRepository, eventPublisher,
                membershipRepository, propertyContactRepository,
                maintenanceScheduleRepository, serviceRecordRepository);
    }

    @Test
    void createSetsDefaultActiveStatus() {
        var property = new Property();
        property.setName("Test Property");

        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = propertyService.create(property);

        assertEquals(PropertyStatus.ACTIVE, result.getStatus());

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(captor.capture());
        assertEquals(PropertyStatus.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void createPreservesExplicitStatus() {
        var property = new Property();
        property.setName("Test Property");
        property.setStatus(PropertyStatus.DISPOSED);

        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = propertyService.create(property);

        assertEquals(PropertyStatus.DISPOSED, result.getStatus());
    }

    @Test
    void createSetsIdAndTimestamps() {
        var property = new Property();
        property.setName("Test Property");

        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = propertyService.create(property);

        assertNotNull(result.getId());

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(captor.capture());
        assertNotNull(captor.getValue().getId());
    }

    @Test
    void updateExistingPropertyUpdatesAndSaves() {
        UUID id = UUID.randomUUID();
        Instant originalCreatedAt = Instant.parse("2025-01-01T00:00:00Z");

        var existing = new Property();
        existing.setId(id);
        existing.setCreatedAt(originalCreatedAt);
        existing.setName("Old Name");

        var updated = new Property();
        updated.setName("New Name");

        when(propertyRepository.findById(id)).thenReturn(Optional.of(existing));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = propertyService.update(id, updated);

        assertEquals(id, result.getId());
        assertEquals(originalCreatedAt, result.getCreatedAt());
        assertEquals("New Name", result.getName());
        verify(propertyRepository).save(updated);
    }

    @Test
    void updateNonexistentPropertyThrowsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(propertyRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> propertyService.update(id, new Property()));
    }

    @Test
    void archiveExistingPropertySetsArchivedAt() {
        UUID id = UUID.randomUUID();
        var existing = new Property();
        existing.setId(id);

        when(propertyRepository.findById(id)).thenReturn(Optional.of(existing));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        propertyService.archive(id);

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(captor.capture());
        assertNotNull(captor.getValue().getArchivedAt());
    }

    @Test
    void archiveNonexistentPropertyThrowsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(propertyRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> propertyService.archive(id));
    }
}
