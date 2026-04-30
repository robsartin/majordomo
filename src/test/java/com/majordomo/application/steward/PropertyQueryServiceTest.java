package com.majordomo.application.steward;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Plain JUnit unit tests for {@link PropertyQueryService} — no Spring. */
class PropertyQueryServiceTest {

    private PropertyRepository propertyRepository;
    private PropertyQueryService service;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void setUp() {
        propertyRepository = mock(PropertyRepository.class);
        service = new PropertyQueryService(propertyRepository);
    }

    /** Cycle 1: returns active rows for the org, sorted by name (case-insensitive, nulls last). */
    @Test
    void listSortsByNameCaseInsensitive() {
        Property beach = property("Beach House", "vacation", "Cape Cod", null);
        Property apt = property("apartment", "rental", "Boston", null);
        when(propertyRepository.findByOrganizationId(ORG_ID))
                .thenReturn(List.of(beach, apt));

        List<Property> rows = service.list(ORG_ID, PropertyFilters.none());

        assertThat(rows).extracting(Property::getName)
                .containsExactly("apartment", "Beach House");
    }

    /** Cycle 2: archived properties are filtered. */
    @Test
    void listSkipsArchived() {
        Property active = property("Active", "rental", "x", null);
        Property archived = property("Archived", "rental", "y",
                Instant.parse("2025-01-01T00:00:00Z"));
        when(propertyRepository.findByOrganizationId(ORG_ID))
                .thenReturn(List.of(active, archived));

        List<Property> rows = service.list(ORG_ID, PropertyFilters.none());

        assertThat(rows).extracting(Property::getName).containsExactly("Active");
    }

    /** Cycle 3: category filter is exact-match, case-insensitive. */
    @Test
    void categoryFilterNarrowsResults() {
        Property cabin = property("Cabin", "vacation", "Maine", null);
        Property apt = property("Apt", "rental", "Boston", null);
        when(propertyRepository.findByOrganizationId(ORG_ID))
                .thenReturn(List.of(cabin, apt));

        List<Property> rows = service.list(ORG_ID, new PropertyFilters("RENTAL", null));

        assertThat(rows).extracting(Property::getName).containsExactly("Apt");
    }

    /** Cycle 4: q matches across name + description. */
    @Test
    void qFilterMatchesNameOrDescription() {
        Property cabin = property("Cabin", "vacation", "Maine", null);
        cabin.setDescription("Lakeside retreat with HVAC");
        Property apt = property("Apt", "rental", "Boston", null);
        apt.setDescription("City rental");
        when(propertyRepository.findByOrganizationId(ORG_ID))
                .thenReturn(List.of(cabin, apt));

        // Match by description.
        assertThat(service.list(ORG_ID, new PropertyFilters(null, "lakeside")))
                .extracting(Property::getName).containsExactly("Cabin");

        // Match by name.
        assertThat(service.list(ORG_ID, new PropertyFilters(null, "apt")))
                .extracting(Property::getName).containsExactly("Apt");

        // No match.
        assertThat(service.list(ORG_ID, new PropertyFilters(null, "nonexistent")))
                .isEmpty();
    }

    private static Property property(String name, String category, String location,
                                     Instant archivedAt) {
        Property p = new Property();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(ORG_ID);
        p.setName(name);
        p.setCategory(category);
        p.setLocation(location);
        p.setStatus(PropertyStatus.ACTIVE);
        p.setArchivedAt(archivedAt);
        return p;
    }
}
