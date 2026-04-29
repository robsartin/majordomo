package com.majordomo.adapter.in.web.ledger;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.ledger.SpendSummary;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link LedgerController}: spend queries under {@code /api/ledger}.
 */
@WebMvcTest(LedgerController.class)
@Import(SecurityConfig.class)
class LedgerControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean QuerySpendUseCase querySpendUseCase;
    @MockitoBean OrganizationAccessService organizationAccessService;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();
    private static final UUID PROPERTY_ID = UuidFactory.newId();

    /** GET /api/ledger/properties/{id}/spend resolves org via property and returns the summary. */
    @Test
    @WithMockUser
    void spendForPropertyReturnsSummary() throws Exception {
        Property property = new Property();
        property.setId(PROPERTY_ID);
        property.setOrganizationId(ORG_ID);
        when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property));
        when(querySpendUseCase.spendForProperty(PROPERTY_ID)).thenReturn(
                new SpendSummary(new BigDecimal("250000.00"),
                        new BigDecimal("1200.00"), new BigDecimal("251200.00")));

        mvc.perform(get("/api/ledger/properties/{propertyId}/spend", PROPERTY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasePrice").value(250000.00))
                .andExpect(jsonPath("$.maintenanceCost").value(1200.00))
                .andExpect(jsonPath("$.totalCost").value(251200.00));

        verify(organizationAccessService).verifyAccess(ORG_ID);
    }

    /** GET /api/ledger/properties/{id}/spend returns 404 + correlation id when property missing. */
    @Test
    @WithMockUser
    void spendForPropertyReturns404WhenPropertyMissing() throws Exception {
        when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/api/ledger/properties/{propertyId}/spend", PROPERTY_ID))
                .andExpect(status().isNotFound());

        verify(querySpendUseCase, never()).spendForProperty(PROPERTY_ID);
    }

    /** GET /api/ledger/organizations/{id}/spend verifies access and returns the org summary. */
    @Test
    @WithMockUser
    void spendForOrganizationReturnsSummary() throws Exception {
        when(querySpendUseCase.spendForOrganization(ORG_ID)).thenReturn(
                new SpendSummary(new BigDecimal("500000.00"),
                        new BigDecimal("3400.50"), new BigDecimal("503400.50")));

        mvc.perform(get("/api/ledger/organizations/{organizationId}/spend", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(503400.50));

        verify(organizationAccessService).verifyAccess(ORG_ID);
    }

    /** Cross-org access on the org-spend endpoint returns 403. */
    @Test
    @WithMockUser
    void spendForOrganizationReturns403WhenAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(ORG_ID);

        mvc.perform(get("/api/ledger/organizations/{organizationId}/spend", ORG_ID))
                .andExpect(status().isForbidden());

        verify(querySpendUseCase, never()).spendForOrganization(ORG_ID);
    }

    /** GET /api/ledger/organizations/{id}/projected-annual returns the projected annual spend. */
    @Test
    @WithMockUser
    void projectedAnnualReturnsBigDecimal() throws Exception {
        when(querySpendUseCase.projectedAnnualSpend(ORG_ID)).thenReturn(new BigDecimal("12345.67"));

        mvc.perform(get("/api/ledger/organizations/{organizationId}/projected-annual", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(12345.67));

        verify(organizationAccessService).verifyAccess(ORG_ID);
    }
}
