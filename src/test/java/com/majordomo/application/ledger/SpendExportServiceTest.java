package com.majordomo.application.ledger;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.ledger.SpendExportRow;
import com.majordomo.domain.model.ledger.SpendSummary;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendExportServiceTest {

    @Mock QuerySpendUseCase spend;
    @Mock PropertyRepository properties;

    private SpendExportService service;
    private final UUID orgId = UuidFactory.newId();

    @BeforeEach
    void setUp() {
        service = new SpendExportService(spend, properties);
    }

    private Property property(String name) {
        Property p = new Property();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(orgId);
        p.setName(name);
        return p;
    }

    @Test
    void buildsAPerPropertyRowSortedByTotalDescPlusAnOrgTotalRow() {
        Property furnace = property("Furnace");
        Property roof = property("Roof");
        when(properties.findByOrganizationId(orgId)).thenReturn(List.of(furnace, roof));
        when(spend.spendForProperty(furnace.getId()))
                .thenReturn(new SpendSummary(new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("150")));
        when(spend.spendForProperty(roof.getId()))
                .thenReturn(new SpendSummary(new BigDecimal("200"), new BigDecimal("30"), new BigDecimal("230")));
        when(spend.spendForOrganization(orgId))
                .thenReturn(new SpendSummary(new BigDecimal("300"), new BigDecimal("80"), new BigDecimal("380")));

        List<SpendExportRow> rows = service.spendRows(orgId);

        // Highest total first, then the rollup row last.
        assertThat(rows).extracting(SpendExportRow::property)
                .containsExactly("Roof", "Furnace", "All properties");
        assertThat(rows.get(0).totalCost()).isEqualByComparingTo("230");
        assertThat(rows.get(2).purchasePrice()).isEqualByComparingTo("300");
        assertThat(rows.get(2).maintenanceCost()).isEqualByComparingTo("80");
        assertThat(rows.get(2).totalCost()).isEqualByComparingTo("380");
    }

    @Test
    void excludesArchivedProperties() {
        Property active = property("Active");
        Property archived = property("Archived");
        archived.setArchivedAt(Instant.now());
        when(properties.findByOrganizationId(orgId)).thenReturn(List.of(active, archived));
        when(spend.spendForProperty(active.getId()))
                .thenReturn(new SpendSummary(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("2")));
        when(spend.spendForOrganization(orgId))
                .thenReturn(new SpendSummary(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("2")));

        List<SpendExportRow> rows = service.spendRows(orgId);

        assertThat(rows).extracting(SpendExportRow::property)
                .containsExactly("Active", "All properties");
    }
}
