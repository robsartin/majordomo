package com.majordomo.adapter.in.web.herald;

import com.majordomo.application.herald.ScheduleAccessGuard;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Thymeleaf controller for the Herald UI. The REST surface lives at
 * {@code /api/schedules} on {@link ScheduleController} and is not touched by
 * this controller — separating the two follows the Envoy convention
 * ({@code /api/envoy/*} REST, {@code /envoy/*} Thymeleaf).
 */
@Controller
public class SchedulePageController {

    private final CurrentOrganizationResolver currentOrg;
    private final ScheduleAccessGuard guard;
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
     * @param scheduleRepository outbound port for schedule reads
     * @param propertyRepository outbound port for property reads
     */
    public SchedulePageController(CurrentOrganizationResolver currentOrg,
                                  ScheduleAccessGuard guard,
                                  MaintenanceScheduleRepository scheduleRepository,
                                  PropertyRepository propertyRepository) {
        this.currentOrg = currentOrg;
        this.guard = guard;
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
}
