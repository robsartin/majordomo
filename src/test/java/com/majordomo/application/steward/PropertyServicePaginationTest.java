package com.majordomo.application.steward;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyServicePaginationTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private EventPublisher eventPublisher;

    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        propertyService = new PropertyService(propertyRepository, eventPublisher);
    }

    @Test
    void findByOrganizationIdFirstPageReturnsItemsAndCursor() {
        UUID orgId = UUID.randomUUID();
        List<Property> threeProperties = createProperties(3);
        when(propertyRepository.findByOrganizationId(orgId, null, 3)).thenReturn(threeProperties);

        Page<Property> page = propertyService.findByOrganizationId(orgId, null, 2);

        assertEquals(2, page.items().size());
        assertTrue(page.hasMore());
        assertNotNull(page.nextCursor());
        assertEquals(threeProperties.get(1).getId(), page.nextCursor());
        verify(propertyRepository).findByOrganizationId(orgId, null, 3);
    }

    @Test
    void findByOrganizationIdLastPageHasMoreFalse() {
        UUID orgId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        List<Property> oneProperty = createProperties(1);
        when(propertyRepository.findByOrganizationId(orgId, cursor, 3)).thenReturn(oneProperty);

        Page<Property> page = propertyService.findByOrganizationId(orgId, cursor, 2);

        assertEquals(1, page.items().size());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
        verify(propertyRepository).findByOrganizationId(orgId, cursor, 3);
    }

    @Test
    void findByOrganizationIdLimitClampedMaxIs100() {
        UUID orgId = UUID.randomUUID();
        when(propertyRepository.findByOrganizationId(orgId, null, 101))
                .thenReturn(createProperties(101));

        Page<Property> page = propertyService.findByOrganizationId(orgId, null, 200);

        assertEquals(100, page.items().size());
        assertTrue(page.hasMore());
        verify(propertyRepository).findByOrganizationId(orgId, null, 101);
    }

    private List<Property> createProperties(int count) {
        List<Property> properties = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var property = new Property();
            property.setId(UUID.randomUUID());
            property.setName("Property " + i);
            properties.add(property);
        }
        return properties;
    }
}
