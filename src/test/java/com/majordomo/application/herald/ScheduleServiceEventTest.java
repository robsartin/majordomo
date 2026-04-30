package com.majordomo.application.herald;

import com.majordomo.domain.model.event.ServiceRecordCreated;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
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
 * Tests that {@link ScheduleService} publishes domain events after state changes.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceEventTest {

    @Mock
    private MaintenanceScheduleRepository scheduleRepository;

    @Mock
    private ServiceRecordRepository serviceRecordRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private PropertyRepository propertyRepository;

    private ScheduleService scheduleService;

    /** Sets up the service under test with mocked dependencies. */
    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository, serviceRecordRepository,
                propertyRepository, eventPublisher);
    }

    /** Verifies that recordService publishes a ServiceRecordCreated event with organizationId. */
    @Test
    void recordServicePublishesServiceRecordCreatedEvent() {
        UUID scheduleId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        var record = new ServiceRecord();
        record.setDescription("Filter replaced");
        record.setPropertyId(propertyId);

        Property property = new Property();
        property.setId(propertyId);
        property.setOrganizationId(orgId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

        when(serviceRecordRepository.save(any(ServiceRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        scheduleService.recordService(scheduleId, record);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(captor.capture());

        var event = assertInstanceOf(ServiceRecordCreated.class, captor.getValue());
        assertNotNull(event.serviceRecordId());
        assertEquals(propertyId, event.propertyId());
        assertEquals(scheduleId, event.scheduleId());
        assertEquals(orgId, event.organizationId());
        assertNotNull(event.occurredAt());
    }
}
