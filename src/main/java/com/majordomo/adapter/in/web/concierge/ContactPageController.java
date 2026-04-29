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
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * Renders the new-contact form.
     *
     * @param principal authenticated user
     * @param model     Thymeleaf model
     * @return the {@code contact-form} template
     */
    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal UserDetails principal, Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        model.addAttribute("editingId", null);
        model.addAttribute("existing", null);
        model.addAttribute("username", ctx.user().getUsername());
        return "contact-form";
    }

    /**
     * Creates a contact from the new-form post and redirects to detail.
     *
     * @param formattedName required formatted display name
     * @param givenName     optional given name
     * @param familyName    optional family name
     * @param organization  optional organization
     * @param title         optional title
     * @param notes         optional free-form notes
     * @param emails        newline-separated email addresses
     * @param telephones    newline-separated telephone numbers
     * @param urls          newline-separated URLs
     * @param nicknames     newline-separated nicknames
     * @param principal     authenticated user
     * @param model         Thymeleaf model
     * @return redirect to the new contact's detail page on success
     */
    @PostMapping
    public String create(@RequestParam(required = false) String formattedName,
                         @RequestParam(required = false) String givenName,
                         @RequestParam(required = false) String familyName,
                         @RequestParam(required = false) String organization,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false) String notes,
                         @RequestParam(required = false) String emails,
                         @RequestParam(required = false) String telephones,
                         @RequestParam(required = false) String urls,
                         @RequestParam(required = false) String nicknames,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        if (formattedName == null || formattedName.isBlank()) {
            populateFormState(model, null, null, ctx.user().getUsername(),
                    "Formatted name is required.", formattedName, givenName, familyName,
                    organization, title, notes, emails, telephones, urls, nicknames);
            return "contact-form";
        }
        List<String> emailList = splitLines(emails);
        String emailError = validateEmails(emailList);
        if (emailError != null) {
            populateFormState(model, null, null, ctx.user().getUsername(), emailError,
                    formattedName, givenName, familyName, organization, title, notes,
                    emails, telephones, urls, nicknames);
            return "contact-form";
        }
        Contact contact = new Contact();
        contact.setOrganizationId(ctx.organizationId());
        contact.setFormattedName(formattedName);
        contact.setGivenName(blankToNull(givenName));
        contact.setFamilyName(blankToNull(familyName));
        contact.setOrganization(blankToNull(organization));
        contact.setTitle(blankToNull(title));
        contact.setNotes(blankToNull(notes));
        contact.setEmails(emailList);
        contact.setTelephones(splitLines(telephones));
        contact.setUrls(splitLines(urls));
        contact.setNicknames(splitLines(nicknames));
        Contact saved = contactUseCase.create(contact);
        return "redirect:/contacts/" + saved.getId();
    }

    /**
     * Renders the edit-contact form, pre-populated.
     *
     * @param id        the contact's UUID
     * @param principal authenticated user
     * @param model     Thymeleaf model
     * @return the {@code contact-form} template
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id,
                           @AuthenticationPrincipal UserDetails principal,
                           Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        Contact existing = contactRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.CONTACT.name(), id));
        organizationAccessService.verifyAccess(existing.getOrganizationId());
        model.addAttribute("editingId", id);
        model.addAttribute("existing", existing);
        model.addAttribute("username", ctx.user().getUsername());
        return "contact-form";
    }

    /**
     * Updates an existing contact from the edit-form post and redirects to detail.
     *
     * @param id            the contact's UUID
     * @param formattedName required formatted display name
     * @param givenName     optional given name
     * @param familyName    optional family name
     * @param organization  optional organization
     * @param title         optional title
     * @param notes         optional notes
     * @param emails        newline-separated email addresses
     * @param telephones    newline-separated telephone numbers
     * @param urls          newline-separated URLs
     * @param nicknames     newline-separated nicknames
     * @param principal     authenticated user
     * @param model         Thymeleaf model
     * @return redirect to the contact's detail page on success
     */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam(required = false) String formattedName,
                         @RequestParam(required = false) String givenName,
                         @RequestParam(required = false) String familyName,
                         @RequestParam(required = false) String organization,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false) String notes,
                         @RequestParam(required = false) String emails,
                         @RequestParam(required = false) String telephones,
                         @RequestParam(required = false) String urls,
                         @RequestParam(required = false) String nicknames,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        Contact existing = contactRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.CONTACT.name(), id));
        organizationAccessService.verifyAccess(existing.getOrganizationId());
        if (formattedName == null || formattedName.isBlank()) {
            populateFormState(model, id, existing, ctx.user().getUsername(),
                    "Formatted name is required.", formattedName, givenName, familyName,
                    organization, title, notes, emails, telephones, urls, nicknames);
            return "contact-form";
        }
        List<String> emailList = splitLines(emails);
        String emailError = validateEmails(emailList);
        if (emailError != null) {
            populateFormState(model, id, existing, ctx.user().getUsername(), emailError,
                    formattedName, givenName, familyName, organization, title, notes,
                    emails, telephones, urls, nicknames);
            return "contact-form";
        }
        Contact updated = new Contact();
        updated.setId(id);
        updated.setOrganizationId(existing.getOrganizationId());
        updated.setFormattedName(formattedName);
        updated.setGivenName(blankToNull(givenName));
        updated.setFamilyName(blankToNull(familyName));
        updated.setOrganization(blankToNull(organization));
        updated.setTitle(blankToNull(title));
        updated.setNotes(blankToNull(notes));
        updated.setEmails(emailList);
        updated.setTelephones(splitLines(telephones));
        updated.setUrls(splitLines(urls));
        updated.setNicknames(splitLines(nicknames));
        contactUseCase.update(id, updated);
        return "redirect:/contacts/" + id;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static List<String> splitLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    /** Returns null if all emails parse, or an error message naming the first bad line. */
    private static String validateEmails(List<String> emails) {
        for (String e : emails) {
            if (e.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(e).matches()) {
                return "Invalid email: " + e;
            }
        }
        return null;
    }

    /** RFC 5321 caps an address path at 254 characters; cap before regex matching. */
    private static final int MAX_EMAIL_LENGTH = 254;

    /**
     * Domain segments are unambiguous because the character classes exclude `.`,
     * so each `+` can only match within one label and there's no backtracking
     * overlap between segments. Local part still excludes whitespace and `@`.
     */
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$");

    private static void populateFormState(Model model, UUID editingId, Contact existing,
                                          String username, String formError,
                                          String formattedName, String givenName, String familyName,
                                          String organization, String title, String notes,
                                          String emails, String telephones, String urls,
                                          String nicknames) {
        model.addAttribute("editingId", editingId);
        model.addAttribute("existing", existing);
        model.addAttribute("username", username);
        model.addAttribute("formError", formError);
        model.addAttribute("formFormattedName", formattedName);
        model.addAttribute("formGivenName", givenName);
        model.addAttribute("formFamilyName", familyName);
        model.addAttribute("formOrganization", organization);
        model.addAttribute("formTitle", title);
        model.addAttribute("formNotes", notes);
        model.addAttribute("formEmails", emails);
        model.addAttribute("formTelephones", telephones);
        model.addAttribute("formUrls", urls);
        model.addAttribute("formNicknames", nicknames);
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
