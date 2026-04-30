package com.majordomo.adapter.in.web.identity;

import com.majordomo.adapter.in.web.config.ApiKeyAuthenticationFilter;
import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.ApiKey;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Account API key management UI. Mints, lists, and revokes API keys scoped to
 * the authenticated user's organization. Sibling to the REST
 * {@link ApiKeyController} at {@code /api/organizations/{orgId}/api-keys},
 * which is untouched.
 */
@Controller
@RequestMapping("/account/api-keys")
public class AccountApiKeyPageController {

    private static final int KEY_RANDOM_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    /**
     * Constructs the API key page controller.
     *
     * @param apiKeyRepository outbound port for API key persistence
     */
    public AccountApiKeyPageController(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Renders the API key list for the user's organization.
     *
     * @param orgContext authenticated user + organization
     * @param model      Thymeleaf model
     * @return the {@code account-api-keys} template
     */
    @GetMapping
    public String list(OrgContext orgContext, Model model) {
        List<ApiKey> keys = apiKeyRepository.findByOrganizationId(orgContext.organizationId()).stream()
                .filter(k -> k.getArchivedAt() == null)
                .toList();
        model.addAttribute("keys", keys);
        model.addAttribute("username", orgContext.username());
        return "account-api-keys";
    }

    /**
     * Mints a new API key for the user's organization. The plaintext value is
     * passed back through a flash attribute so the redirected list page can
     * display it once. SHA-256 of the plaintext is persisted; the plaintext is
     * never stored.
     *
     * @param name       the key label (required)
     * @param orgContext authenticated user + organization
     * @param redirect   redirect attributes (used as flash for one-shot plaintext)
     * @return redirect back to the list
     */
    @PostMapping
    public String create(@RequestParam(required = false) String name,
                         OrgContext orgContext,
                         RedirectAttributes redirect) {
        if (name == null || name.isBlank()) {
            redirect.addFlashAttribute("formError", "Name is required.");
            return "redirect:/account/api-keys";
        }
        String plaintext = generateRawKey();
        String hashed = ApiKeyAuthenticationFilter.sha256(plaintext);
        Instant now = Instant.now();
        ApiKey key = new ApiKey(UuidFactory.newId(), orgContext.organizationId(), name.trim(), hashed);
        key.setCreatedAt(now);
        key.setUpdatedAt(now);
        apiKeyRepository.save(key);
        redirect.addFlashAttribute("plaintextKey", plaintext);
        redirect.addFlashAttribute("plaintextKeyName", name.trim());
        return "redirect:/account/api-keys";
    }

    /**
     * Revokes (soft-deletes) an API key by setting {@code archivedAt}.
     * Cross-organization revokes are silently ignored — the redirect lands on
     * the same list, which won't contain the foreign key.
     *
     * @param id         the key UUID
     * @param orgContext authenticated user + organization
     * @return redirect back to the list
     */
    @PostMapping("/{id}/revoke")
    public String revoke(@PathVariable UUID id, OrgContext orgContext) {
        UUID orgId = orgContext.organizationId();
        apiKeyRepository.findById(id).ifPresent(k -> {
            if (orgId.equals(k.getOrganizationId())) {
                Instant now = Instant.now();
                k.setArchivedAt(now);
                k.setUpdatedAt(now);
                apiKeyRepository.save(k);
            }
        });
        return "redirect:/account/api-keys";
    }

    private static String generateRawKey() {
        byte[] bytes = new byte[KEY_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
