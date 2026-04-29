package com.majordomo.adapter.in.web.herald;

import com.majordomo.application.herald.ScheduleAccessGuard;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Thymeleaf controller for the Herald UI. The REST surface lives at
 * {@code /api/schedules} on {@link com.majordomo.adapter.in.web.herald.ScheduleController}
 * and is not touched by this controller — separating the two follows the Envoy
 * convention ({@code /api/envoy/*} REST, {@code /envoy/*} Thymeleaf).
 */
@Controller
public class SchedulePageController {

    private final CurrentOrganizationResolver currentOrg;
    private final ScheduleAccessGuard guard;
    private final ManageScheduleUseCase scheduleUseCase;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final PropertyRepository propertyRepository;

    /**
     * One row in the {@code /schedules} list.
     *
     * @param schedule    the schedule
     * @param property    the owning property (never {@code null} — the auth guard
     *                    ensures the user can see every row's property)
     * @param daysUntilDue {@code null} if {@code nextDue} is null; otherwise the
     *                    integer day delta (negative = overdue, 0 = today)
     */
    public record ScheduleRow(
            MaintenanceSchedule schedule, Property property, Integer daysUntilDue) { }

    /**
     * Constructs the controller.
     *
     * @param currentOrg         resolves the authenticated user's organizations
     * @param guard              authorization helper for property-scoped reads
     * @param scheduleUseCase    inbound port for schedule writes (record-add)
     * @param scheduleRepository outbound port for schedule reads
     * @param propertyRepository outbound port for property reads
     */
    public SchedulePageController(CurrentOrganizationResolver currentOrg,
                                  ScheduleAccessGuard guard,
                                  ManageScheduleUseCase scheduleUseCase,
                                  MaintenanceScheduleRepository scheduleRepository,
                                  PropertyRepository propertyRepository) {
        this.currentOrg = currentOrg;
        this.guard = guard;
        this.scheduleUseCase = scheduleUseCase;
        this.scheduleRepository = scheduleRepository;
        this.propertyRepository = propertyRepository;
    }

    /**
     * Renders the schedule list page.
     *
     * @param frequency      optional Frequency filter (null = any)
     * @param dueWithinDays  optional "due within N days" filter (null = any)
     * @param principal      authenticated user
     * @param model          Thymeleaf model
     * @return the {@code schedules} template, or a redirect to {@code /} when
     *         the user has no organization
     */
    @GetMapping("/schedules")
    public String list(@RequestParam(required = false) Frequency frequency,
                       @RequestParam(required = false) Integer dueWithinDays,
                       @AuthenticationPrincipal UserDetails principal,
                       Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }

        Set<UUID> orgIds = guard.currentUserOrganizationIds();
        // Pull all properties + their schedules (N+1 by property is fine at personal scale).
        List<Property> properties = new ArrayList<>();
        for (UUID orgId : orgIds) {
            properties.addAll(propertyRepository.findByOrganizationId(orgId));
        }

        LocalDate today = LocalDate.now();
        LocalDate dueCutoff = dueWithinDays == null ? null : today.plusDays(dueWithinDays);

        List<ScheduleRow> rows = new ArrayList<>();
        for (Property property : properties) {
            for (MaintenanceSchedule s : scheduleRepository.findByPropertyId(property.getId())) {
                if (s.getArchivedAt() != null) {
                    continue;
                }
                if (frequency != null && s.getFrequency() != frequency) {
                    continue;
                }
                if (dueCutoff != null && (s.getNextDue() == null || s.getNextDue().isAfter(dueCutoff))) {
                    continue;
                }
                Integer days = s.getNextDue() == null ? null
                        : (int) ChronoUnit.DAYS.between(today, s.getNextDue());
                rows.add(new ScheduleRow(s, property, days));
            }
        }
        // Soonest-due first; nulls (no nextDue set) sink to the bottom.
        rows.sort(Comparator.comparing(
                (ScheduleRow r) -> r.daysUntilDue() == null ? Integer.MAX_VALUE : r.daysUntilDue()));

        model.addAttribute("rows", rows);
        model.addAttribute("frequency", frequency);
        model.addAttribute("dueWithinDays", dueWithinDays);
        model.addAttribute("frequencies", Frequency.values());
        model.addAttribute("username", ctx.user().getUsername());
        return "schedules";
    }

    /**
     * Renders the detail page for a single schedule, including its full
     * service-record history (most recent first) and an inline form to log a
     * new service event.
     *
     * @param id        schedule id
     * @param principal authenticated user
     * @param model     Thymeleaf model
     * @return the {@code schedule-detail} template, or {@code redirect:/} if
     *         the user has no organization
     */
    @GetMapping("/schedules/{id}")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        guard.verifyForSchedule(id);
        renderDetail(id, ctx.user().getUsername(), model);
        return "schedule-detail";
    }

    /**
     * Records a completed service event against the given schedule, then
     * redirects to {@code GET /schedules/{id}} so the new entry renders at the
     * top. On validation failure the detail page is re-rendered with field
     * state preserved and a {@code recordError} attribute set.
     *
     * @param id          schedule id
     * @param performedOn date the service was performed (required)
     * @param description short description (required, non-blank)
     * @param cost        optional cost
     * @param notes       optional free-form notes
     * @param principal   authenticated user
     * @param model       Thymeleaf model
     * @return redirect to the detail page on success; {@code schedule-detail}
     *         on a handled validation failure; {@code redirect:/} if no org
     */
    @PostMapping("/schedules/{id}/records")
    public String addRecord(@PathVariable UUID id,
                            @RequestParam(required = false) String performedOn,
                            @RequestParam(required = false) String description,
                            @RequestParam(required = false) BigDecimal cost,
                            @RequestParam(required = false) String notes,
                            @AuthenticationPrincipal UserDetails principal,
                            Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        guard.verifyForSchedule(id);

        String error = validateRecordInputs(performedOn, description);
        if (error != null) {
            renderDetail(id, ctx.user().getUsername(), model);
            model.addAttribute("recordError", error);
            // Echo posted values back so the form keeps state.
            model.addAttribute("formPerformedOn", performedOn);
            model.addAttribute("formDescription", description);
            model.addAttribute("formCost", cost);
            model.addAttribute("formNotes", notes);
            return "schedule-detail";
        }

        ServiceRecord record = new ServiceRecord();
        record.setPerformedOn(LocalDate.parse(performedOn));
        record.setDescription(description);
        record.setCost(cost);
        record.setNotes(notes);
        scheduleUseCase.recordService(id, record);
        return "redirect:/schedules/" + id;
    }

    /**
     * Populates the model with everything the detail template needs.
     */
    private void renderDetail(UUID id, String username, Model model) {
        MaintenanceSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        EntityType.MAINTENANCE_SCHEDULE.name(), id));
        Property property = propertyRepository.findById(schedule.getPropertyId()).orElse(null);
        List<ServiceRecord> records = new ArrayList<>(
                scheduleUseCase.findRecordsByScheduleId(id));
        records.sort(Comparator.comparing(ServiceRecord::getPerformedOn,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Integer days = schedule.getNextDue() == null ? null
                : (int) ChronoUnit.DAYS.between(LocalDate.now(), schedule.getNextDue());

        model.addAttribute("schedule", schedule);
        model.addAttribute("property", property);
        model.addAttribute("records", records);
        model.addAttribute("daysUntilDue", days);
        model.addAttribute("username", username);
    }

    private static String validateRecordInputs(String performedOn, String description) {
        if (performedOn == null || performedOn.isBlank()) {
            return "Performed-on date is required.";
        }
        try {
            LocalDate.parse(performedOn);
        } catch (DateTimeParseException ex) {
            return "Performed-on must be a valid date (YYYY-MM-DD).";
        }
        if (description == null || description.isBlank()) {
            return "Description is required.";
        }
        return null;
    }
}
