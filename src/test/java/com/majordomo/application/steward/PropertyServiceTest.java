package com.majordomo.application.steward;

import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        propertyService = new PropertyService(propertyRepository);
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
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(captor.capture());
        assertNotNull(captor.getValue().getId());
        assertNotNull(captor.getValue().getCreatedAt());
        assertNotNull(captor.getValue().getUpdatedAt());
    }
}
