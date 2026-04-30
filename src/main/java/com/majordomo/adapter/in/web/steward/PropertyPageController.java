package com.majordomo.adapter.in.web.steward;

import com.majordomo.adapter.in.web.FormBindingHelper;
import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.application.steward.PropertyDetailView;
import com.majordomo.application.steward.PropertyDetailViewService;
import com.majordomo.application.steward.PropertyFilters;
import com.majordomo.application.steward.PropertyQueryService;
import com.majordomo.domain.model.concierge.ContactRole;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
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
    private final PropertyRepository propertyRepository;
    private final PropertyQueryService propertyQueryService;
    private final PropertyDetailViewService propertyDetailViewService;
    private final CurrentOrganizationResolver currentOrg;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the property page controller.
     *
     * @param propertyUseCase           the inbound port for property management
     * @param propertyRepository        the outbound port for property reads (used by parent picker)
     * @param propertyQueryService      application service for the list view's filter+sort
     * @param propertyDetailViewService application service that assembles the detail view
     * @param currentOrg                resolves the authenticated user's organization
     * @param organizationAccessService verifies the caller has access to a given organization
     */
    public PropertyPageController(ManagePropertyUseCase propertyUseCase,
                                  PropertyRepository propertyRepository,
                                  PropertyQueryService propertyQueryService,
                                  PropertyDetailViewService propertyDetailViewService,
                                  CurrentOrganizationResolver currentOrg,
                                  OrganizationAccessService organizationAccessService) {
        this.propertyUseCase = propertyUseCase;
        this.propertyRepository = propertyRepository;
        this.propertyQueryService = propertyQueryService;
        this.propertyDetailViewService = propertyDetailViewService;
        this.currentOrg = currentOrg;
        this.organizationAccessService = organizationAccessService;
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
        List<Property> rows = propertyQueryService.list(ctx.organizationId(),
                new PropertyFilters(category, q));
        model.addAttribute("rows", rows);
        model.addAttribute("category", category);
        model.addAttribute("q", q);
        model.addAttribute("username", ctx.user().getUsername());
        return "properties";
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
        model.addAttribute("parentCandidates", candidateParents(ctx.organizationId(), null));
        return "property-form";
    }

    /**
     * Creates a property from the new-form post and redirects to the detail page.
     *
     * @param name          property name (required)
     * @param category      optional category
     * @param description   optional description
     * @param location      optional address / location
     * @param purchasePrice optional purchase price (decimal)
     * @param parentId      optional parent property ID
     * @param principal     authenticated user
     * @param model         Thymeleaf model
     * @return redirect to the new property's detail page on success
     */
    @PostMapping
    public String create(@RequestParam(required = false) String name,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String location,
                         @RequestParam(required = false) String purchasePrice,
                         @RequestParam(required = false) String parentId,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        var fields = new PropertyFormFields(name, category, description, location, purchasePrice);
        if (name == null || name.isBlank()) {
            populateFormState(model, null, null, ctx.user().getUsername(),
                    "Name is required.", fields);
            return "property-form";
        }
        java.math.BigDecimal price;
        try {
            price = parsePrice(purchasePrice);
        } catch (PriceFormatException ex) {
            populateFormState(model, null, null, ctx.user().getUsername(),
                    ex.getMessage(), fields);
            return "property-form";
        }
        Property property = new Property();
        property.setOrganizationId(ctx.organizationId());
        property.setName(name);
        property.setCategory(category);
        property.setDescription(FormBindingHelper.blankToNull(description));
        property.setLocation(FormBindingHelper.blankToNull(location));
        property.setPurchasePrice(price);
        property.setParentId(FormBindingHelper.parseUuid(parentId));
        Property saved = propertyUseCase.create(property);
        return "redirect:/properties/" + saved.getId();
    }

    /**
     * Returns properties in the given organization that are valid as a parent for
     * {@code editingId} (or for a new property when {@code editingId} is null).
     * Excludes the property itself and all of its descendants to prevent cycles.
     * Sorted by name (case-insensitive).
     */
    private List<Property> candidateParents(UUID organizationId, UUID editingId) {
        java.util.Set<UUID> excluded = new java.util.HashSet<>();
        if (editingId != null) {
            excluded.add(editingId);
            collectDescendantIds(editingId, excluded);
        }
        List<Property> all = new ArrayList<>(
                propertyRepository.findByOrganizationId(organizationId));
        all.removeIf(p -> p.getArchivedAt() != null || excluded.contains(p.getId()));
        all.sort(Comparator.comparing(
                Property::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return all;
    }

    private void collectDescendantIds(UUID rootId, java.util.Set<UUID> sink) {
        for (Property child : propertyUseCase.findByParentId(rootId)) {
            if (sink.add(child.getId())) {
                collectDescendantIds(child.getId(), sink);
            }
        }
    }

    private static java.math.BigDecimal parsePrice(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        java.math.BigDecimal value;
        try {
            value = new java.math.BigDecimal(s.trim());
        } catch (NumberFormatException ex) {
            throw new PriceFormatException("Purchase price must be a number.");
        }
        if (value.signum() < 0) {
            throw new PriceFormatException("Purchase price must be non-negative.");
        }
        return value;
    }

    private static final class PriceFormatException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        PriceFormatException(String message) {
            super(message);
        }
    }

    /**
     * Bundle of property-form field strings echoed back when re-rendering on
     * a validation failure. Reduces what was a 9-arg call into a small record.
     *
     * @param name          submitted name
     * @param category      submitted category
     * @param description   submitted description
     * @param location      submitted location
     * @param purchasePrice submitted purchase-price string (raw, unparsed)
     */
    private record PropertyFormFields(String name, String category, String description,
                                      String location, String purchasePrice) { }

    private static void populateFormState(Model model, UUID editingId, Property existing,
                                          String username, String formError,
                                          PropertyFormFields fields) {
        model.addAttribute("editingId", editingId);
        model.addAttribute("existing", existing);
        model.addAttribute("username", username);
        model.addAttribute("formError", formError);
        model.addAttribute("formName", fields.name());
        model.addAttribute("formCategory", fields.category());
        model.addAttribute("formDescription", fields.description());
        model.addAttribute("formLocation", fields.location());
        model.addAttribute("formPurchasePrice", fields.purchasePrice());
    }

    /**
     * Updates an existing property from the edit-form post and redirects to detail.
     *
     * @param id            the UUID of the property to update
     * @param name          property name (required)
     * @param category      optional category
     * @param description   optional description
     * @param location      optional address / location
     * @param purchasePrice optional purchase price (decimal)
     * @param parentId      optional parent property ID
     * @param principal     authenticated user
     * @param model         Thymeleaf model
     * @return redirect to the property's detail page on success
     */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam(required = false) String name,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String location,
                         @RequestParam(required = false) String purchasePrice,
                         @RequestParam(required = false) String parentId,
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
        var fields = new PropertyFormFields(name, category, description, location, purchasePrice);
        if (name == null || name.isBlank()) {
            populateFormState(model, id, existing, ctx.user().getUsername(),
                    "Name is required.", fields);
            return "property-form";
        }
        java.math.BigDecimal price;
        try {
            price = parsePrice(purchasePrice);
        } catch (PriceFormatException ex) {
            populateFormState(model, id, existing, ctx.user().getUsername(),
                    ex.getMessage(), fields);
            return "property-form";
        }
        Property updated = new Property();
        updated.setId(id);
        updated.setOrganizationId(existing.getOrganizationId());
        updated.setName(name);
        updated.setCategory(category);
        updated.setDescription(FormBindingHelper.blankToNull(description));
        updated.setLocation(FormBindingHelper.blankToNull(location));
        updated.setPurchasePrice(price);
        updated.setParentId(FormBindingHelper.parseUuid(parentId));
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
        model.addAttribute("parentCandidates",
                candidateParents(existing.getOrganizationId(), id));
        return "property-form";
    }

    /**
     * Renders the property detail page for the given property ID.
     *
     * @param id         the UUID of the property to display
     * @param orgContext authenticated user + organization
     * @param model      the Thymeleaf model
     * @return the property-detail template name
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id,
                         OrgContext orgContext,
                         Model model) {
        PropertyDetailView view = propertyDetailViewService.assemble(id);
        model.addAttribute("property", view.property());
        model.addAttribute("parent", view.parent());
        model.addAttribute("children", view.children());
        model.addAttribute("propertyContacts", view.propertyContacts());
        model.addAttribute("contacts", view.linkedContacts());
        model.addAttribute("contactCandidates", view.contactCandidates());
        model.addAttribute("contactRoles", ContactRole.values());
        model.addAttribute("scheduleRows", view.scheduleRows());
        model.addAttribute("recentRecords", view.recentRecords());
        model.addAttribute("attachments", view.attachments());
        model.addAttribute("username", orgContext.username());
        return "property-detail";
    }
}
