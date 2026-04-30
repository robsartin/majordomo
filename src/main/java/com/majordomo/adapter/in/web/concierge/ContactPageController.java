package com.majordomo.adapter.in.web.concierge;

import com.majordomo.adapter.in.web.FormBindingHelper;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.concierge.Address;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyContact;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
    private final PropertyRepository propertyRepository;
    private final PropertyContactRepository propertyContactRepository;
    private final CurrentOrganizationResolver currentOrg;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the contact page controller.
     *
     * @param contactUseCase            inbound port for contact management
     * @param contactRepository         outbound port for contact reads (used by list view)
     * @param propertyRepository        outbound port for property reads (link picker, batch hydration)
     * @param propertyContactRepository outbound port for property–contact associations
     * @param currentOrg                resolves the authenticated user's organization
     * @param organizationAccessService verifies caller has access to a given organization
     */
    public ContactPageController(ManageContactUseCase contactUseCase,
                                 ContactRepository contactRepository,
                                 PropertyRepository propertyRepository,
                                 PropertyContactRepository propertyContactRepository,
                                 CurrentOrganizationResolver currentOrg,
                                 OrganizationAccessService organizationAccessService) {
        this.contactUseCase = contactUseCase;
        this.contactRepository = contactRepository;
        this.propertyRepository = propertyRepository;
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

        List<PropertyContact> activeLinks = propertyContactRepository.findByContactId(id).stream()
                .filter(pc -> pc.getArchivedAt() == null)
                .toList();
        java.util.Set<UUID> linkedIds = activeLinks.stream()
                .map(PropertyContact::getPropertyId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<UUID, Property> propertiesById = propertyRepository.findByIdIn(linkedIds).stream()
                .collect(java.util.stream.Collectors.toMap(Property::getId, p -> p));
        List<LinkedPropertyRow> linkedRows = activeLinks.stream()
                .map(pc -> {
                    Property p = propertiesById.get(pc.getPropertyId());
                    return p == null ? null : new LinkedPropertyRow(pc, p);
                })
                .filter(r -> r != null)
                .sorted(Comparator.comparing((LinkedPropertyRow r) -> r.property().getName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<Property> propertyCandidates =
                propertyRepository.findByOrganizationId(contact.getOrganizationId()).stream()
                        .filter(p -> p.getArchivedAt() == null)
                        .filter(p -> !linkedIds.contains(p.getId()))
                        .sorted(Comparator.comparing(Property::getName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                        .toList();

        model.addAttribute("contact", contact);
        model.addAttribute("linkedRows", linkedRows);
        model.addAttribute("propertyCandidates", propertyCandidates);
        model.addAttribute("contactRoles",
                com.majordomo.domain.model.concierge.ContactRole.values());
        model.addAttribute("username", ctx.user().getUsername());
        return "contact-detail";
    }

    /**
     * View-model row tying a property to its PropertyContact link id (needed by
     * the unlink form on the contact-detail page).
     *
     * @param link     the underlying PropertyContact row
     * @param property the linked property
     */
    public record LinkedPropertyRow(PropertyContact link, Property property) { }

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
     * @param command       indexed addresses sub-form
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
                         @ModelAttribute ContactFormCommand command,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        var fields = new ContactFormFields(formattedName, givenName, familyName,
                organization, title, notes, emails, telephones, urls, nicknames);
        if (formattedName == null || formattedName.isBlank()) {
            populateFormState(model, null, null, ctx.user().getUsername(),
                    "Formatted name is required.", fields);
            return "contact-form";
        }
        List<String> emailList = FormBindingHelper.splitLines(emails);
        String emailError = validateEmails(emailList);
        if (emailError != null) {
            populateFormState(model, null, null, ctx.user().getUsername(), emailError, fields);
            return "contact-form";
        }
        Contact contact = new Contact();
        contact.setOrganizationId(ctx.organizationId());
        contact.setFormattedName(formattedName);
        contact.setGivenName(FormBindingHelper.blankToNull(givenName));
        contact.setFamilyName(FormBindingHelper.blankToNull(familyName));
        contact.setOrganization(FormBindingHelper.blankToNull(organization));
        contact.setTitle(FormBindingHelper.blankToNull(title));
        contact.setNotes(FormBindingHelper.blankToNull(notes));
        contact.setEmails(emailList);
        contact.setTelephones(FormBindingHelper.splitLines(telephones));
        contact.setUrls(FormBindingHelper.splitLines(urls));
        contact.setNicknames(FormBindingHelper.splitLines(nicknames));
        contact.setAddresses(toAddresses(command.getAddresses(), null));
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
     * @param command       indexed addresses sub-form
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
                         @ModelAttribute ContactFormCommand command,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        Contact existing = contactRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(EntityType.CONTACT.name(), id));
        organizationAccessService.verifyAccess(existing.getOrganizationId());
        var fields = new ContactFormFields(formattedName, givenName, familyName,
                organization, title, notes, emails, telephones, urls, nicknames);
        if (formattedName == null || formattedName.isBlank()) {
            populateFormState(model, id, existing, ctx.user().getUsername(),
                    "Formatted name is required.", fields);
            return "contact-form";
        }
        List<String> emailList = FormBindingHelper.splitLines(emails);
        String emailError = validateEmails(emailList);
        if (emailError != null) {
            populateFormState(model, id, existing, ctx.user().getUsername(), emailError, fields);
            return "contact-form";
        }
        Contact updated = new Contact();
        updated.setId(id);
        updated.setOrganizationId(existing.getOrganizationId());
        updated.setFormattedName(formattedName);
        updated.setGivenName(FormBindingHelper.blankToNull(givenName));
        updated.setFamilyName(FormBindingHelper.blankToNull(familyName));
        updated.setOrganization(FormBindingHelper.blankToNull(organization));
        updated.setTitle(FormBindingHelper.blankToNull(title));
        updated.setNotes(FormBindingHelper.blankToNull(notes));
        updated.setEmails(emailList);
        updated.setTelephones(FormBindingHelper.splitLines(telephones));
        updated.setUrls(FormBindingHelper.splitLines(urls));
        updated.setNicknames(FormBindingHelper.splitLines(nicknames));
        updated.setAddresses(toAddresses(command.getAddresses(), id));
        contactUseCase.update(id, updated);
        return "redirect:/contacts/" + id;
    }

    /** Drops blank rows; mints a fresh row id for each remaining row. */
    private static List<Address> toAddresses(List<AddressFormRow> rows, UUID contactId) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Address> out = new ArrayList<>();
        for (AddressFormRow r : rows) {
            if (r == null || r.isBlank()) {
                continue;
            }
            out.add(new Address(
                    UuidFactory.newId(), contactId,
                    FormBindingHelper.blankToNull(r.getLabel()), FormBindingHelper.blankToNull(r.getStreet()),
                    FormBindingHelper.blankToNull(r.getCity()), FormBindingHelper.blankToNull(r.getState()),
                    FormBindingHelper.blankToNull(r.getPostalCode()), FormBindingHelper.blankToNull(r.getCountry())));
        }
        return out;
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

    /**
     * Bundle of contact-form field strings echoed back when re-rendering on
     * a validation failure. Reduces what was a 14-arg call into a record.
     *
     * @param formattedName submitted formatted name (display)
     * @param givenName     submitted given name
     * @param familyName    submitted family name
     * @param organization  submitted organization
     * @param title         submitted title
     * @param notes         submitted notes
     * @param emails        submitted multi-line emails (raw)
     * @param telephones    submitted multi-line phones (raw)
     * @param urls          submitted multi-line URLs (raw)
     * @param nicknames     submitted multi-line nicknames (raw)
     */
    private record ContactFormFields(String formattedName, String givenName, String familyName,
                                     String organization, String title, String notes,
                                     String emails, String telephones, String urls,
                                     String nicknames) { }

    private static void populateFormState(Model model, UUID editingId, Contact existing,
                                          String username, String formError,
                                          ContactFormFields fields) {
        model.addAttribute("editingId", editingId);
        model.addAttribute("existing", existing);
        model.addAttribute("username", username);
        model.addAttribute("formError", formError);
        model.addAttribute("formFormattedName", fields.formattedName());
        model.addAttribute("formGivenName", fields.givenName());
        model.addAttribute("formFamilyName", fields.familyName());
        model.addAttribute("formOrganization", fields.organization());
        model.addAttribute("formTitle", fields.title());
        model.addAttribute("formNotes", fields.notes());
        model.addAttribute("formEmails", fields.emails());
        model.addAttribute("formTelephones", fields.telephones());
        model.addAttribute("formUrls", fields.urls());
        model.addAttribute("formNicknames", fields.nicknames());
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
