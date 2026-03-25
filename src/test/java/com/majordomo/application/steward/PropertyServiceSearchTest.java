package com.majordomo.application.steward;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.steward.Property;
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

/**
 * Unit tests for property search functionality in {@link PropertyService}.
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceSearchTest {

    @Mock
    private PropertyRepository propertyRepository;

    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        propertyService = new PropertyService(propertyRepository);
    }

    @Test
    void searchReturnsPageWithCursorWhenMoreResults() {
        UUID orgId = UUID.randomUUID();
        String query = "garage";
        List<Property> threeProperties = createProperties(3);
        when(propertyRepository.search(orgId, query, null, null, null, 3))
                .thenReturn(threeProperties);

        Page<Property> page = propertyService.search(orgId, query, null, null, null, 2);

        assertEquals(2, page.items().size());
        assertTrue(page.hasMore());
        assertNotNull(page.nextCursor());
        assertEquals(threeProperties.get(1).getId(), page.nextCursor());
        verify(propertyRepository).search(orgId, query, null, null, null, 3);
    }

    @Test
    void searchReturnsLastPageWithNoMoreResults() {
        UUID orgId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        String query = "shed";
        List<Property> oneProperty = createProperties(1);
        when(propertyRepository.search(orgId, query, null, null, cursor, 3))
                .thenReturn(oneProperty);

        Page<Property> page = propertyService.search(orgId, query, null, null, cursor, 2);

        assertEquals(1, page.items().size());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
        verify(propertyRepository).search(orgId, query, null, null, cursor, 3);
    }

    @Test
    void searchPassesCategoryAndStatusFilters() {
        UUID orgId = UUID.randomUUID();
        String query = "main";
        String category = "HVAC";
        String status = "ACTIVE";
        when(propertyRepository.search(orgId, query, category, status, null, 21))
                .thenReturn(createProperties(5));

        Page<Property> page = propertyService.search(orgId, query, category, status, null, 20);

        assertEquals(5, page.items().size());
        assertFalse(page.hasMore());
        verify(propertyRepository).search(orgId, query, category, status, null, 21);
    }

    @Test
    void searchClampsLimitToMax100() {
        UUID orgId = UUID.randomUUID();
        String query = "test";
        when(propertyRepository.search(orgId, query, null, null, null, 101))
                .thenReturn(createProperties(101));

        Page<Property> page = propertyService.search(orgId, query, null, null, null, 200);

        assertEquals(100, page.items().size());
        assertTrue(page.hasMore());
        verify(propertyRepository).search(orgId, query, null, null, null, 101);
    }

    @Test
    void searchReturnsEmptyPageWhenNoResults() {
        UUID orgId = UUID.randomUUID();
        String query = "nonexistent";
        when(propertyRepository.search(orgId, query, null, null, null, 21))
                .thenReturn(List.of());

        Page<Property> page = propertyService.search(orgId, query, null, null, null, 20);

        assertTrue(page.items().isEmpty());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
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
