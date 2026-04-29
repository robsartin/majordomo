package com.majordomo.adapter.in.web.audit;

import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.AuditLogRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serves the audit log viewer at {@code /audit}. Reads-only — writes are
 * driven by the {@code AuditEventListener}.
 */
@Controller
public class AuditPageController {

    private static final int DEFAULT_PAGE_LIMIT = 50;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final CurrentOrganizationResolver currentOrg;

    /**
     * View-model row pairing an audit entry with the resolved actor username.
     *
     * @param entry          the underlying audit-log entry
     * @param actorUsername  the actor's username, or {@code null} if not resolvable
     */
    public record AuditRow(AuditLogEntry entry, String actorUsername) { }

    /**
     * Constructs the audit page controller.
     *
     * @param auditLogRepository outbound port for audit-log queries
     * @param userRepository     outbound port for user lookups (actor resolution)
     * @param currentOrg         resolves the authenticated user's organization
     */
    public AuditPageController(AuditLogRepository auditLogRepository,
                               UserRepository userRepository,
                               CurrentOrganizationResolver currentOrg) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.currentOrg = currentOrg;
    }

    /**
     * Renders the audit log table for the user's organization.
     *
     * @param entityType optional entity-type filter
     * @param actor      optional actor-username filter
     * @param since      optional inclusive lower-bound date (yyyy-MM-dd)
     * @param until      optional exclusive upper-bound date (yyyy-MM-dd)
     * @param principal  authenticated user
     * @param model      Thymeleaf model
     * @return the {@code audit} template, or a redirect home if no org
     */
    @GetMapping("/audit")
    public String list(@RequestParam(required = false) String entityType,
                       @RequestParam(required = false) String actor,
                       @RequestParam(required = false) String since,
                       @RequestParam(required = false) String until,
                       @AuthenticationPrincipal UserDetails principal,
                       Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        UUID orgId = ctx.organizationId();

        String entityTypeFilter = blankToNull(entityType);
        UUID actorId = resolveActor(actor);
        Instant sinceInstant = parseDate(since);
        Instant untilInstant = parseDate(until);

        List<AuditLogEntry> entries = auditLogRepository.find(
                orgId, entityTypeFilter, actorId, sinceInstant, untilInstant, DEFAULT_PAGE_LIMIT);

        Map<UUID, String> usernamesById = new HashMap<>();
        List<AuditRow> rows = new java.util.ArrayList<>(entries.size());
        for (AuditLogEntry entry : entries) {
            String username = null;
            if (entry.getUserId() != null) {
                username = usernamesById.computeIfAbsent(entry.getUserId(),
                        uid -> userRepository.findById(uid).map(User::getUsername).orElse(null));
            }
            rows.add(new AuditRow(entry, username));
        }

        model.addAttribute("rows", rows);
        model.addAttribute("entityType", entityType);
        model.addAttribute("actor", actor);
        model.addAttribute("since", since);
        model.addAttribute("until", until);
        model.addAttribute("entityTypeOptions", entityTypeOptions(entries));
        model.addAttribute("username", ctx.user().getUsername());
        return "audit";
    }

    private UUID resolveActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(actor.trim())
                .map(User::getId)
                .orElse(null);
    }

    private static Instant parseDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        return LocalDate.parse(iso.trim()).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Ordered set of entity types appearing in the current result page (for the filter dropdown). */
    private static List<String> entityTypeOptions(List<AuditLogEntry> entries) {
        Map<String, Boolean> ordered = new LinkedHashMap<>();
        for (AuditLogEntry e : entries) {
            if (e.getEntityType() != null) {
                ordered.putIfAbsent(e.getEntityType(), Boolean.TRUE);
            }
        }
        return List.copyOf(ordered.keySet());
    }
}
