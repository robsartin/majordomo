package com.majordomo.adapter.in.web.steward;

import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyContact;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Serves the property detail web page for authenticated users.
 *
 * <p>Loads all data associated with a single property — child properties, linked
 * contacts, maintenance schedules, and file attachments — and exposes it to the
 * Thymeleaf template via the model.</p>
 */
@Controller
@RequestMapping("/properties")
public class PropertyPageController {

    private final ManagePropertyUseCase propertyUseCase;
    private final ManageScheduleUseCase scheduleUseCase;
    private final ManageContactUseCase contactUseCase;
    private final ManageAttachmentUseCase attachmentUseCase;
    private final PropertyContactRepository propertyContactRepository;
    private final PropertyRepository propertyRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final CurrentOrganizationResolver currentOrg;
    private final OrganizationAccessService organizationAccessService;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    /** Maximum service-records to surface in the "Recent service records" panel. */
    private static final int RECENT_RECORDS_LIMIT = 10;

    /**
     * View-model row binding a {@link MaintenanceSchedule} to its days-until-due
     * delta for the property-detail page.
     *
     * @param schedule    the schedule
     * @param daysUntilDue {@code null} if {@code nextDue} is null; otherwise the
     *                    integer day delta (negative = overdue, 0 = today)
     */
    public record ScheduleRow(MaintenanceSchedule schedule, Integer daysUntilDue) { }

    /**
     * Constructs the property page controller.
     *
     * @param propertyUseCase           the inbound port for property management
     * @param scheduleUseCase           the inbound port for maintenance schedule management
     * @param contactUseCase            the inbound port for contact management
     * @param attachmentUseCase         the inbound port for attachment management
     * @param propertyContactRepository the outbound port for property-contact associations
     * @param propertyRepository        the outbound port for property reads (used by the list view)
     * @param serviceRecordRepository   the outbound port for service-record reads (recent activity panel)
     * @param currentOrg                resolves the authenticated user's organization
     * @param organizationAccessService verifies the caller has access to a given organization
     * @param userRepository            the outbound port for user lookups
     * @param membershipRepository      the outbound port for membership lookups
     */
    public PropertyPageController(ManagePropertyUseCase propertyUseCase,
                                  ManageScheduleUseCase scheduleUseCase,
                                  ManageContactUseCase contactUseCase,
                                  ManageAttachmentUseCase attachmentUseCase,
                                  PropertyContactRepository propertyContactRepository,
                                  PropertyRepository propertyRepository,
                                  ServiceRecordRepository serviceRecordRepository,
                                  CurrentOrganizationResolver currentOrg,
                                  OrganizationAccessService organizationAccessService,
                                  UserRepository userRepository,
                                  MembershipRepository membershipRepository) {
        this.propertyUseCase = propertyUseCase;
        this.scheduleUseCase = scheduleUseCase;
        this.contactUseCase = contactUseCase;
        this.attachmentUseCase = attachmentUseCase;
        this.propertyContactRepository = propertyContactRepository;
        this.propertyRepository = propertyRepository;
        this.serviceRecordRepository = serviceRecordRepository;
        this.currentOrg = currentOrg;
        this.organizationAccessService = organizationAccessService;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Renders the property list page for the authenticated user's organization,
     * with optional category and free-text filters.
     *
     * @param category  optional exact-match category filter
     * @param q         optional case-insensitive query across name + description
     * @param principal authenticated user
     * @param model     Thymeleaf model
     * @return the {@code properties} template, or a redirect home if the user
     *         has no organization
     */
    @GetMapping
    public String list(@RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       @AuthenticationPrincipal UserDetails principal,
                       Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        UUID orgId = ctx.organizationId();
        List<Property> raw = new ArrayList<>(propertyRepository.findByOrganizationId(orgId));

        String categoryFilter = (category == null || category.isBlank()) ? null : category.trim();
        String qLower = (q == null || q.isBlank()) ? null : q.trim().toLowerCase();

        List<Property> rows = new ArrayList<>();
        for (Property p : raw) {
            if (p.getArchivedAt() != null) {
                continue;
            }
            if (categoryFilter != null && !categoryFilter.equalsIgnoreCase(p.getCategory())) {
                continue;
            }
            if (qLower != null && !matchesQuery(p, qLower)) {
                continue;
            }
            rows.add(p);
        }
        rows.sort(Comparator.comparing(
                Property::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        model.addAttribute("rows", rows);
        model.addAttribute("category", category);
        model.addAttribute("q", q);
        model.addAttribute("username", ctx.user().getUsername());
        return "properties";
    }

    private static boolean matchesQuery(Property p, String qLower) {
        if (p.getName() != null && p.getName().toLowerCase().contains(qLower)) {
            return true;
        }
        if (p.getDescription() != null && p.getDescription().toLowerCase().contains(qLower)) {
            return true;
        }
        return false;
    }

    /**
     * Renders the new-property form.
     *
     * @param principal authenticated user
     * @param model     Thymeleaf model
     * @return the {@code property-form} template
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
        return "property-form";
    }

    /**
     * Creates a property from the new-form post and redirects to the detail page.
     *
     * @param name       property name (required)
     * @param category   optional category
     * @param principal  authenticated user
     * @param model      Thymeleaf model
     * @return redirect to the new property's detail page on success
     */
    @PostMapping
    public String create(@RequestParam(required = false) String name,
                         @RequestParam(required = false) String category,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        if (name == null || name.isBlank()) {
            model.addAttribute("editingId", null);
            model.addAttribute("existing", null);
            model.addAttribute("username", ctx.user().getUsername());
            model.addAttribute("formError", "Name is required.");
            model.addAttribute("formName", name);
            model.addAttribute("formCategory", category);
            return "property-form";
        }
        Property property = new Property();
        property.setOrganizationId(ctx.organizationId());
        property.setName(name);
        property.setCategory(category);
        Property saved = propertyUseCase.create(property);
        return "redirect:/properties/" + saved.getId();
    }

    /**
     * Updates an existing property from the edit-form post and redirects to detail.
     *
     * @param id        the UUID of the property to update
     * @param name      property name (required)
     * @param category  optional category
     * @param principal authenticated user
     * @param model     Thymeleaf model
     * @return redirect to the property's detail page on success
     */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam(required = false) String name,
                         @RequestParam(required = false) String category,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        Property existing = propertyUseCase.findById(id)
                .orElseThrow(() -> new com.majordomo.domain.model.EntityNotFoundException(
                        com.majordomo.domain.model.EntityType.PROPERTY.name(), id));
        organizationAccessService.verifyAccess(existing.getOrganizationId());
        if (name == null || name.isBlank()) {
            model.addAttribute("editingId", id);
            model.addAttribute("existing", existing);
            model.addAttribute("username", ctx.user().getUsername());
            model.addAttribute("formError", "Name is required.");
            model.addAttribute("formName", name);
            model.addAttribute("formCategory", category);
            return "property-form";
        }
        Property updated = new Property();
        updated.setId(id);
        updated.setOrganizationId(existing.getOrganizationId());
        updated.setName(name);
        updated.setCategory(category);
        propertyUseCase.update(id, updated);
        return "redirect:/properties/" + id;
    }

    /**
     * Renders the edit form for an existing property, pre-populated.
     *
     * @param id        the UUID of the property to edit
     * @param principal authenticated user
     * @param model     Thymeleaf model
     * @return the {@code property-form} template
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id,
                           @AuthenticationPrincipal UserDetails principal,
                           Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        Property existing = propertyUseCase.findById(id)
                .orElseThrow(() -> new com.majordomo.domain.model.EntityNotFoundException(
                        com.majordomo.domain.model.EntityType.PROPERTY.name(), id));
        organizationAccessService.verifyAccess(existing.getOrganizationId());
        model.addAttribute("editingId", id);
        model.addAttribute("existing", existing);
        model.addAttribute("username", ctx.user().getUsername());
        return "property-form";
    }

    /**
     * Renders the property detail page for the given property ID.
     *
     * @param id        the UUID of the property to display
     * @param principal the authenticated user
     * @param model     the Thymeleaf model
     * @return the property-detail template name, or a redirect if the property is not found
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        var propertyOpt = propertyUseCase.findById(id);
        if (propertyOpt.isEmpty()) {
            return "redirect:/dashboard";
        }
        Property property = propertyOpt.get();

        Property parent = null;
        if (property.getParentId() != null) {
            parent = propertyUseCase.findById(property.getParentId()).orElse(null);
        }

        List<Property> children = propertyUseCase.findByParentId(id);

        List<PropertyContact> propertyContacts = propertyContactRepository.findByPropertyId(id);
        List<Contact> contacts = propertyContacts.stream()
                .map(pc -> contactUseCase.findById(pc.getContactId()).orElse(null))
                .filter(c -> c != null)
                .toList();

        LocalDate today = LocalDate.now();
        List<ScheduleRow> scheduleRows = new ArrayList<>();
        for (MaintenanceSchedule s : scheduleUseCase.findByPropertyId(id)) {
            if (s.getArchivedAt() != null) {
                continue;
            }
            Integer days = s.getNextDue() == null ? null
                    : (int) ChronoUnit.DAYS.between(today, s.getNextDue());
            scheduleRows.add(new ScheduleRow(s, days));
        }
        scheduleRows.sort(Comparator.comparing(
                (ScheduleRow r) -> r.daysUntilDue() == null ? Integer.MAX_VALUE : r.daysUntilDue()));

        List<ServiceRecord> recentRecords = new ArrayList<>(
                serviceRecordRepository.findByPropertyId(id));
        recentRecords.removeIf(r -> r.getArchivedAt() != null);
        recentRecords.sort(Comparator.comparing(
                ServiceRecord::getPerformedOn, Comparator.nullsLast(Comparator.reverseOrder())));
        if (recentRecords.size() > RECENT_RECORDS_LIMIT) {
            recentRecords = new ArrayList<>(recentRecords.subList(0, RECENT_RECORDS_LIMIT));
        }

        var attachments = attachmentUseCase.list("property", id);

        var user = userRepository.findByUsername(principal.getUsername()).orElseThrow();

        model.addAttribute("property", property);
        model.addAttribute("parent", parent);
        model.addAttribute("children", children);
        model.addAttribute("propertyContacts", propertyContacts);
        model.addAttribute("contacts", contacts);
        model.addAttribute("scheduleRows", scheduleRows);
        model.addAttribute("recentRecords", recentRecords);
        model.addAttribute("attachments", attachments);
        model.addAttribute("username", user.getUsername());

        return "property-detail";
    }
}
