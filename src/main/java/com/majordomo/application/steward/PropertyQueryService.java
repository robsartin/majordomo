package com.majordomo.application.steward;

import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Read-side query service for the property list page. Pulls the org's
 * properties through the repository, applies optional in-memory filters, and
 * returns a sorted result (case-insensitive by name, nulls last).
 *
 * <p>Lives in the application layer so the controller stays thin and the
 * filter logic is unit-testable without Spring.</p>
 */
@Service
public class PropertyQueryService {

    private final PropertyRepository propertyRepository;

    /**
     * Constructs the query service.
     *
     * @param propertyRepository outbound port for property reads
     */
    public PropertyQueryService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /**
     * Lists active (non-archived) properties in the given organization,
     * narrowed by the supplied filters.
     *
     * @param organizationId the organization to query
     * @param filters        category + q filters (use {@link PropertyFilters#none()} for none)
     * @return matching properties, sorted by name (case-insensitive, nulls last)
     */
    public List<Property> list(UUID organizationId, PropertyFilters filters) {
        String categoryFilter = blank(filters.category()) ? null : filters.category().trim();
        String qLower = blank(filters.q()) ? null : filters.q().trim().toLowerCase();

        return propertyRepository.findByOrganizationId(organizationId).stream()
                .filter(p -> p.getArchivedAt() == null)
                .filter(p -> categoryFilter == null
                        || categoryFilter.equalsIgnoreCase(p.getCategory()))
                .filter(p -> qLower == null || matchesQuery(p, qLower))
                .sorted(Comparator.comparing(Property::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private static boolean matchesQuery(Property p, String qLower) {
        return (p.getName() != null && p.getName().toLowerCase().contains(qLower))
                || (p.getDescription() != null && p.getDescription().toLowerCase().contains(qLower));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
