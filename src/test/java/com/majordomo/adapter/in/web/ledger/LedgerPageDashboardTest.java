package com.majordomo.adapter.in.web.ledger;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.ledger.SpendSummary;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Slice tests for the {@code /ledger} dashboard (#237). */
@WebMvcTest(LedgerPageController.class)
@Import(SecurityConfig.class)
class LedgerPageDashboardTest {

    @Autowired MockMvc mvc;

    @MockitoBean QuerySpendUseCase spendUseCase;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    /** Cycle 1: dashboard renders org spend cards + per-property rows. */
    @Test
    @WithMockUser
    void dashboardRendersSummaryAndPerPropertyRows() throws Exception {
        UUID propA = UuidFactory.newId();
        UUID propB = UuidFactory.newId();
        Property a = property(propA, "Beach House");
        Property b = property(propB, "Mountain Cabin");
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(a, b));

        when(spendUseCase.spendForOrganization(ORG_ID))
                .thenReturn(new SpendSummary(
                        new BigDecimal("750000.00"),
                        new BigDecimal("12500.00"),
                        new BigDecimal("762500.00")));
        when(spendUseCase.projectedAnnualSpend(ORG_ID))
                .thenReturn(new BigDecimal("4800.00"));
        when(spendUseCase.spendForProperty(propA))
                .thenReturn(new SpendSummary(
                        new BigDecimal("450000.00"),
                        new BigDecimal("9000.00"),
                        new BigDecimal("459000.00")));
        when(spendUseCase.spendForProperty(propB))
                .thenReturn(new SpendSummary(
                        new BigDecimal("300000.00"),
                        new BigDecimal("3500.00"),
                        new BigDecimal("303500.00")));

        MvcResult result = mvc.perform(get("/ledger"))
                .andExpect(status().isOk())
                .andExpect(view().name("ledger"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Org-level summary cards.
        assertThat(body)
                .contains("$762,500.00")
                .contains("$750,000.00")
                .contains("$12,500.00")
                .contains("$4,800.00");
        // Per-property rows.
        assertThat(body)
                .contains("Beach House").contains("$459,000.00")
                .contains("Mountain Cabin").contains("$303,500.00");
        // Sorted by total cost descending: Beach House (459k) before Mountain Cabin (303k).
        int beachIdx = body.indexOf("Beach House");
        int mountainIdx = body.indexOf("Mountain Cabin");
        assertThat(beachIdx).isPositive();
        assertThat(mountainIdx).isGreaterThan(beachIdx);
        // Row links to /properties/{id}.
        assertThat(body).contains("/properties/" + propA);
    }

    /** Cycle 2: redirect home when user has no org. */
    @Test
    @WithMockUser
    void redirectHomeWhenNoOrg() throws Exception {
        User user = new User(UuidFactory.newId(), "lonely", "lonely@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, null));

        mvc.perform(get("/ledger"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

    /** Cycle 2b: empty state when no (non-archived) properties. */
    @Test
    @WithMockUser
    void emptyStateWhenNoProperties() throws Exception {
        Property archived = property(UuidFactory.newId(), "Old place");
        archived.setArchivedAt(Instant.now());
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(archived));
        when(spendUseCase.spendForOrganization(ORG_ID))
                .thenReturn(new SpendSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(spendUseCase.projectedAnnualSpend(ORG_ID)).thenReturn(BigDecimal.ZERO);

        MvcResult result = mvc.perform(get("/ledger"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("No properties to summarize")
                .doesNotContain("Old place");
    }

    private static Property property(UUID id, String name) {
        Property p = new Property();
        p.setId(id);
        p.setOrganizationId(ORG_ID);
        p.setName(name);
        return p;
    }
}
