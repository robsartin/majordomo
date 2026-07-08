package com.majordomo.adapter.in.web.ledger;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.ledger.SpendExportRow;
import com.majordomo.domain.port.in.ledger.ExportSpendUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for the ledger CSV export endpoint (#288). */
@WebMvcTest(LedgerExportController.class)
@Import({SecurityConfig.class, SpendCsvWriter.class})
class LedgerExportControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ExportSpendUseCase exportSpend;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean OAuth2UserService oAuth2UserService;
    @MockitoBean ApiKeyRepository apiKeyRepository;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    @Test
    @WithMockUser
    void exportReturnsCsvAttachment() throws Exception {
        var amount = new BigDecimal("100");
        when(exportSpend.spendRows(ORG_ID)).thenReturn(List.of(
                new SpendExportRow("Furnace", amount, new BigDecimal("50"), new BigDecimal("150")),
                new SpendExportRow("All properties", amount, new BigDecimal("50"), new BigDecimal("150"))));

        mvc.perform(get("/ledger/export.csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("majordomo-spend.csv")))
                .andExpect(content().string(containsString("Property,Purchase price,Maintenance cost,Total cost")))
                .andExpect(content().string(containsString("Furnace,100,50,150")))
                .andExpect(content().string(containsString("All properties,100,50,150")));
    }
}
