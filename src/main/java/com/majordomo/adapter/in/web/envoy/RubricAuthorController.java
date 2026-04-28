package com.majordomo.adapter.in.web.envoy;

import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import com.majordomo.domain.port.in.envoy.ManageRubricUseCase;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Thymeleaf controller for the rubric author UI. Renders a list of all
 * rubrics visible to the active org and an edit form that
 * authors/versions a rubric without hand-writing JSON.
 *
 * <p>This controller is the UI counterpart to {@link RubricController} (the
 * JSON {@code PUT /api/envoy/rubrics/{name}} endpoint) and reuses the same
 * {@link ManageRubricUseCase}; both surfaces produce the same persisted
 * domain model.</p>
 */
@Controller
public class RubricAuthorController {

    private final ManageRubricUseCase rubrics;
    private final RubricRepository rubricRepository;
    private final CurrentOrganizationResolver currentOrg;

    /**
     * Constructs the controller.
     *
     * @param rubrics          inbound port for rubric authoring
     * @param rubricRepository outbound port for rubric reads
     * @param currentOrg       resolves the authenticated user's first organization
     */
    public RubricAuthorController(ManageRubricUseCase rubrics,
                                  RubricRepository rubricRepository,
                                  CurrentOrganizationResolver currentOrg) {
        this.rubrics = rubrics;
        this.rubricRepository = rubricRepository;
        this.currentOrg = currentOrg;
    }

    /**
     * Renders the list of active rubrics visible to the authenticated user's
     * first organization (org-specific actives plus system-default rubrics
     * the org has not yet authored).
     *
     * @param principal the authenticated user
     * @param model     the Thymeleaf model
     * @return the {@code rubrics-list} template, or a redirect home if the
     *         user has no organization
     */
    @GetMapping("/envoy/rubrics")
    public String list(@AuthenticationPrincipal UserDetails principal, Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        UUID orgId = ctx.organizationId();
        List<Rubric> activeRubrics = rubricRepository.findActiveRubricsForOrg(orgId);
        model.addAttribute("rubrics", activeRubrics);
        model.addAttribute("organizationId", orgId);
        model.addAttribute("username", ctx.user().getUsername());
        return "rubrics-list";
    }

    /**
     * Renders the edit form for the named rubric. Pre-populates from the active
     * rubric for the org if one exists; otherwise renders a blank form for
     * authoring a new rubric under that name.
     *
     * @param name      logical rubric name (path variable)
     * @param principal the authenticated user
     * @param model     the Thymeleaf model
     * @return the {@code rubric-edit} template, or a redirect home if the
     *         user has no organization
     */
    @GetMapping("/envoy/rubrics/{name}/edit")
    public String editForm(@PathVariable String name,
                           @AuthenticationPrincipal UserDetails principal,
                           Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        UUID orgId = ctx.organizationId();
        Optional<Rubric> existing = rubricRepository.findActiveByName(name, orgId);
        RubricFormDto form = existing.map(RubricAuthorController::toForm)
                .orElseGet(RubricFormDto::new);

        model.addAttribute("form", form);
        model.addAttribute("rubricName", name);
        model.addAttribute("isNew", existing.isEmpty());
        model.addAttribute("organizationId", orgId);
        model.addAttribute("username", ctx.user().getUsername());
        return "rubric-edit";
    }

    /**
     * Handles the submitted rubric form. On validation success, calls
     * {@link ManageRubricUseCase#saveNewVersion(String, Rubric, UUID)} and
     * redirects to the list page with a flash message; on failure re-renders
     * the form with the submitted values and field-level errors preserved.
     *
     * @param name      logical rubric name (path variable)
     * @param form      the submitted form (auto-bound)
     * @param result    binding/validation result
     * @param principal the authenticated user
     * @param model     the Thymeleaf model used for re-render on error
     * @param redirect  flash attribute target on success
     * @return the list redirect on success, or {@code rubric-edit} on error;
     *         redirect home if user has no organization
     */
    @PostMapping("/envoy/rubrics/{name}")
    public String submit(@PathVariable String name,
                         @ModelAttribute("form") RubricFormDto form,
                         BindingResult result,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model,
                         RedirectAttributes redirect) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        UUID orgId = ctx.organizationId();

        validate(form, result);
        if (result.hasErrors()) {
            model.addAttribute("rubricName", name);
            model.addAttribute("isNew", rubricRepository.findActiveByName(name, orgId).isEmpty());
            model.addAttribute("organizationId", orgId);
            model.addAttribute("username", ctx.user().getUsername());
            return "rubric-edit";
        }

        Rubric submitted = toDomain(name, form);
        Rubric saved = rubrics.saveNewVersion(name, submitted, orgId);
        redirect.addFlashAttribute("flashMessage",
                "Saved rubric \"" + name + "\" as version " + saved.version() + ".");
        return "redirect:/envoy/rubrics";
    }

    private void validate(RubricFormDto form, BindingResult result) {
        if (form.getCategories() == null || form.getCategories().isEmpty()) {
            result.rejectValue("categories", "categories.empty",
                    "At least one category is required.");
        } else {
            for (int i = 0; i < form.getCategories().size(); i++) {
                var c = form.getCategories().get(i);
                if (!StringUtils.hasText(c.getKey())) {
                    result.rejectValue("categories[" + i + "].key", "category.key.blank",
                            "Category key is required.");
                }
                if (c.getMaxPoints() == null || c.getMaxPoints() < 0) {
                    result.rejectValue("categories[" + i + "].maxPoints",
                            "category.maxPoints.invalid",
                            "Max points must be zero or greater.");
                }
                if (c.getTiers() == null || c.getTiers().isEmpty()) {
                    result.rejectValue("categories[" + i + "].tiers", "category.tiers.empty",
                            "Each category needs at least one tier.");
                } else {
                    for (int j = 0; j < c.getTiers().size(); j++) {
                        var t = c.getTiers().get(j);
                        if (!StringUtils.hasText(t.getLabel())) {
                            result.rejectValue("categories[" + i + "].tiers[" + j + "].label",
                                    "tier.label.blank", "Tier label is required.");
                        }
                        if (t.getPoints() == null) {
                            result.rejectValue("categories[" + i + "].tiers[" + j + "].points",
                                    "tier.points.required", "Tier points are required.");
                        }
                    }
                }
            }
        }

        var th = form.getThresholds();
        if (th == null || th.getApplyImmediately() == null
                || th.getApply() == null || th.getConsiderOnly() == null) {
            result.rejectValue("thresholds", "thresholds.required",
                    "All three thresholds are required.");
        } else if (!(th.getApplyImmediately() > th.getApply()
                && th.getApply() > th.getConsiderOnly())) {
            result.rejectValue("thresholds", "thresholds.ordering",
                    "Thresholds must satisfy applyImmediately > apply > considerOnly.");
        }
    }

    private static RubricFormDto toForm(Rubric r) {
        var dto = new RubricFormDto();
        List<RubricFormDto.CategoryForm> cats = new ArrayList<>();
        for (Category c : r.categories()) {
            var cf = new RubricFormDto.CategoryForm();
            cf.setKey(c.key());
            cf.setDescription(c.description());
            cf.setMaxPoints(c.maxPoints());
            List<RubricFormDto.TierForm> tiers = new ArrayList<>();
            for (Tier t : c.tiers()) {
                var tf = new RubricFormDto.TierForm();
                tf.setLabel(t.label());
                tf.setPoints(t.points());
                tf.setCriteria(t.criteria());
                tiers.add(tf);
            }
            cf.setTiers(tiers);
            cats.add(cf);
        }
        dto.setCategories(cats);
        var thresholds = new RubricFormDto.ThresholdsForm();
        thresholds.setApplyImmediately(r.thresholds().applyImmediately());
        thresholds.setApply(r.thresholds().apply());
        thresholds.setConsiderOnly(r.thresholds().considerOnly());
        dto.setThresholds(thresholds);
        return dto;
    }

    private static Rubric toDomain(String name, RubricFormDto form) {
        List<Category> categories = new ArrayList<>();
        for (var cf : form.getCategories()) {
            List<Tier> tiers = new ArrayList<>();
            for (var tf : cf.getTiers()) {
                tiers.add(new Tier(
                        tf.getLabel(),
                        tf.getPoints() == null ? 0 : tf.getPoints(),
                        tf.getCriteria()));
            }
            categories.add(new Category(
                    cf.getKey(),
                    cf.getDescription(),
                    cf.getMaxPoints() == null ? 0 : cf.getMaxPoints(),
                    tiers));
        }
        var t = form.getThresholds();
        var thresholds = new Thresholds(
                t.getApplyImmediately(),
                t.getApply(),
                t.getConsiderOnly());
        // id, organizationId, version, effectiveFrom are assigned by the
        // service; supply placeholders that the service will overwrite.
        return new Rubric(
                UuidFactory.newId(),
                Optional.empty(),
                0,
                name,
                List.of(),
                categories,
                List.of(),
                thresholds,
                Instant.now());
    }

}
