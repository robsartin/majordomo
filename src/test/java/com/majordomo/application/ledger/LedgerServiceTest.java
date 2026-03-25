package com.majordomo.application.ledger;

import com.majordomo.domain.model.ledger.SpendSummary;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.ledger.LedgerQueryRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LedgerService}.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private LedgerQueryRepository ledgerQueryRepository;

    private LedgerService ledgerService;

    /** Sets up the service under test with mocked dependencies. */
    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(propertyRepository,
                ledgerQueryRepository);
    }

    /** Verifies that spendForProperty returns the correct summary. */
    @Test
    void spendForPropertyReturnsCorrectSummary() {
        UUID propertyId = UUID.randomUUID();
        Property property = new Property();
        property.setId(propertyId);
        property.setPurchasePrice(new BigDecimal("500.00"));

        when(propertyRepository.findById(propertyId))
                .thenReturn(Optional.of(property));
        when(ledgerQueryRepository.totalMaintenanceCost(propertyId))
                .thenReturn(new BigDecimal("200.00"));

        SpendSummary summary = ledgerService.spendForProperty(propertyId);

        assertEquals(new BigDecimal("500.00"), summary.purchasePrice());
        assertEquals(new BigDecimal("200.00"), summary.maintenanceCost());
        assertEquals(new BigDecimal("700.00"), summary.totalCost());
    }

    /** Verifies that a null purchase price is treated as zero. */
    @Test
    void spendForPropertyNullPurchasePriceTreatsAsZero() {
        UUID propertyId = UUID.randomUUID();
        Property property = new Property();
        property.setId(propertyId);
        property.setPurchasePrice(null);

        when(propertyRepository.findById(propertyId))
                .thenReturn(Optional.of(property));
        when(ledgerQueryRepository.totalMaintenanceCost(propertyId))
                .thenReturn(new BigDecimal("150.00"));

        SpendSummary summary = ledgerService.spendForProperty(propertyId);

        assertEquals(BigDecimal.ZERO, summary.purchasePrice());
        assertEquals(new BigDecimal("150.00"), summary.maintenanceCost());
        assertEquals(new BigDecimal("150.00"), summary.totalCost());
    }

    /** Verifies that an unknown property throws IllegalArgumentException. */
    @Test
    void spendForPropertyUnknownPropertyThrowsException() {
        UUID propertyId = UUID.randomUUID();

        when(propertyRepository.findById(propertyId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> ledgerService.spendForProperty(propertyId));
    }

    /** Verifies that spendForOrganization aggregates all properties. */
    @Test
    void spendForOrganizationAggregatesAllProperties() {
        UUID orgId = UUID.randomUUID();

        Property property1 = new Property();
        property1.setPurchasePrice(new BigDecimal("300.00"));

        Property property2 = new Property();
        property2.setPurchasePrice(new BigDecimal("400.00"));

        when(propertyRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(property1, property2));
        when(ledgerQueryRepository
                .totalMaintenanceCostByOrganization(orgId))
                .thenReturn(new BigDecimal("100.00"));

        SpendSummary summary = ledgerService.spendForOrganization(orgId);

        assertEquals(new BigDecimal("700.00"), summary.purchasePrice());
        assertEquals(new BigDecimal("100.00"), summary.maintenanceCost());
        assertEquals(new BigDecimal("800.00"), summary.totalCost());
    }
}
