package com.majordomo.adapter.in.web.concierge;

import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Serves the Concierge web pages for contacts. Sibling to the REST
 * {@code ContactController} at {@code /api/contacts}, which is untouched.
 */
@Controller
@RequestMapping("/contacts")
public class ContactPageController {

    private final ManageContactUseCase contactUseCase;
    private final ContactRepository contactRepository;
    private final ManagePropertyUseCase propertyUseCase;
    private final PropertyContactRepository propertyContactRepository;
    private final CurrentOrganizationResolver currentOrg;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the contact page controller.
     *
     * @param contactUseCase            inbound port for contact management
     * @param contactRepository         outbound port for contact reads (used by list view)
     * @param propertyUseCase           inbound port for property lookups
     * @param propertyContactRepository outbound port for property–contact associations
     * @param currentOrg                resolves the authenticated user's organization
     * @param organizationAccessService verifies caller has access to a given organization
     */
    public ContactPageController(ManageContactUseCase contactUseCase,
                                 ContactRepository contactRepository,
                                 ManagePropertyUseCase propertyUseCase,
                                 PropertyContactRepository propertyContactRepository,
                                 CurrentOrganizationResolver currentOrg,
                                 OrganizationAccessService organizationAccessService) {
        this.contactUseCase = contactUseCase;
        this.contactRepository = contactRepository;
        this.propertyUseCase = propertyUseCase;
        this.propertyContactRepository = propertyContactRepository;
        this.currentOrg = currentOrg;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Renders the contact list page for the user's organization, with optional
     * organization-filter and free-text query.
     *
     * @param organization optional exact-match organization filter
     * @param q            optional case-insensitive query (name + organization + emails)
     * @param principal    authenticated user
     * @param model        Thymeleaf model
     * @return the {@code contacts} template
     */
    @GetMapping
    public String list(@RequestParam(required = false) String organization,
                       @RequestParam(required = false) String q,
                       @AuthenticationPrincipal UserDetails principal,
                       Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        UUID orgId = ctx.organizationId();
        List<Contact> raw = new ArrayList<>(contactRepository.findByOrganizationId(orgId));

        String organizationFilter =
                (organization == null || organization.isBlank()) ? null : organization.trim();
        String qLower = (q == null || q.isBlank()) ? null : q.trim().toLowerCase();

        List<Contact> rows = new ArrayList<>();
        for (Contact c : raw) {
            if (c.getArchivedAt() != null) {
                continue;
            }
            if (organizationFilter != null
                    && !organizationFilter.equalsIgnoreCase(c.getOrganization())) {
                continue;
            }
            if (qLower != null && !matchesQuery(c, qLower)) {
                continue;
            }
            rows.add(c);
        }
        rows.sort(Comparator.comparing(Contact::getFormattedName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        // Distinct organizations (non-blank) for the filter dropdown.
        List<String> organizations = raw.stream()
                .filter(c -> c.getArchivedAt() == null)
                .map(Contact::getOrganization)
                .filter(o -> o != null && !o.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        model.addAttribute("rows", rows);
        model.addAttribute("organizations", organizations);
        model.addAttribute("organization", organization);
        model.addAttribute("q", q);
        model.addAttribute("username", ctx.user().getUsername());
        return "contacts";
    }

    /**
     * Renders the contact detail page.
     *
     * @param id        contact UUID
     * @param principal authenticated user
     * @param model     Thymeleaf model
     * @return the {@code contact-detail} template
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        Contact contact = contactRepository.findById(id)
                .filter(c -> c.getArchivedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.CONTACT.name(), id));
        organizationAccessService.verifyAccess(contact.getOrganizationId());

        List<Property> linkedProperties = propertyContactRepository.findByContactId(id).stream()
                .map(pc -> propertyUseCase.findById(pc.getPropertyId()).orElse(null))
                .filter(p -> p != null)
                .sorted(Comparator.comparing(Property::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        model.addAttribute("contact", contact);
        model.addAttribute("linkedProperties", linkedProperties);
        model.addAttribute("username", ctx.user().getUsername());
        return "contact-detail";
    }

    private static boolean matchesQuery(Contact c, String qLower) {
        if (c.getFormattedName() != null && c.getFormattedName().toLowerCase().contains(qLower)) {
            return true;
        }
        if (c.getOrganization() != null && c.getOrganization().toLowerCase().contains(qLower)) {
            return true;
        }
        if (c.getEmails() != null) {
            for (String e : c.getEmails()) {
                if (e != null && e.toLowerCase().contains(qLower)) {
                    return true;
                }
            }
        }
        return false;
    }
}
