package com.majordomo.application.steward;

import com.majordomo.domain.model.event.PropertyArchived;
import com.majordomo.domain.model.steward.Property;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link PropertyService} publishes domain events after state changes.
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceEventTest {

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

    /** Sets up the service under test with mocked dependencies. */
    @BeforeEach
    void setUp() {
        propertyService = new PropertyService(propertyRepository, eventPublisher,
                membershipRepository, propertyContactRepository,
                maintenanceScheduleRepository, serviceRecordRepository);
    }

    /** Verifies that archive publishes a PropertyArchived event. */
    @Test
    void archivePublishesPropertyArchivedEvent() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        var existing = new Property();
        existing.setId(id);
        existing.setOrganizationId(orgId);

        when(propertyRepository.findById(id)).thenReturn(Optional.of(existing));
        when(propertyRepository.save(any(Property.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        propertyService.archive(id);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(captor.capture());

        var event = assertInstanceOf(PropertyArchived.class, captor.getValue());
        assertEquals(id, event.propertyId());
        assertEquals(orgId, event.organizationId());
        assertNotNull(event.occurredAt());
    }
}
