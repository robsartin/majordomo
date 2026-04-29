package com.majordomo.adapter.in.web.identity;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.ApiKey;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Slice tests for the {@code /account/api-keys} management UI (#243). */
@WebMvcTest(AccountApiKeyPageController.class)
@Import(SecurityConfig.class)
class AccountApiKeyPageControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    /** GET /account/api-keys lists keys for the user's org, hiding archived. */
    @Test
    @WithMockUser
    void listRendersOrgKeysExcludingArchived() throws Exception {
        ApiKey active = key("CI runner", false);
        ApiKey revoked = key("Old key", true);
        when(apiKeyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(active, revoked));

        MvcResult result = mvc.perform(get("/account/api-keys"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-api-keys"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("CI runner").doesNotContain("Old key");
    }

    /** POST mints a key, persists hashed value, redirects with plaintext flash for one-time display. */
    @Test
    @WithMockUser
    void createMintsKeyAndShowsPlaintextOnce() throws Exception {
        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MvcResult result = mvc.perform(post("/account/api-keys")
                        .with(csrf())
                        .param("name", "CI runner"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/account/api-keys"))
                .andReturn();

        // Plaintext key passed via flash attribute.
        Object plaintext = result.getFlashMap().get("plaintextKey");
        assertThat(plaintext).isInstanceOf(String.class);
        assertThat((String) plaintext).hasSize(64); // 32 bytes hex-encoded

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("CI runner");
        assertThat(saved.getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(saved.getHashedKey()).hasSize(64); // SHA-256 hex digest
        assertThat(saved.getHashedKey()).isNotEqualTo(plaintext);
    }

    /** POST with blank name re-renders with error (flash) and does not save. */
    @Test
    @WithMockUser
    void createWithBlankNameRendersErrorAndDoesNotSave() throws Exception {
        MvcResult result = mvc.perform(post("/account/api-keys")
                        .with(csrf())
                        .param("name", ""))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getFlashMap().get("formError")).isEqualTo("Name is required.");
        verify(apiKeyRepository, never()).save(any());
    }

    /** POST /account/api-keys/{id}/revoke soft-deletes (sets archivedAt), only when org matches. */
    @Test
    @WithMockUser
    void revokeSoftDeletesOwnKey() throws Exception {
        UUID keyId = UuidFactory.newId();
        ApiKey existing = key("CI runner", false);
        existing.setId(keyId);
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(existing));
        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/account/api-keys/{id}/revoke", keyId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        assertThat(captor.getValue().getArchivedAt()).isNotNull();
    }

    /** Cross-org revoke is silently ignored (404 not surfaced — page redirects + flashes "key not found"). */
    @Test
    @WithMockUser
    void revokeCrossOrgKeyIsIgnored() throws Exception {
        UUID keyId = UuidFactory.newId();
        ApiKey foreign = key("Other org", false);
        foreign.setId(keyId);
        foreign.setOrganizationId(UuidFactory.newId());
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(foreign));

        mvc.perform(post("/account/api-keys/{id}/revoke", keyId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(apiKeyRepository, never()).save(any());
    }

    private static ApiKey key(String name, boolean archived) {
        ApiKey k = new ApiKey();
        k.setId(UuidFactory.newId());
        k.setOrganizationId(ORG_ID);
        k.setName(name);
        k.setHashedKey("0".repeat(64));
        k.setCreatedAt(Instant.now());
        if (archived) {
            k.setArchivedAt(Instant.now());
        }
        return k;
    }
}
