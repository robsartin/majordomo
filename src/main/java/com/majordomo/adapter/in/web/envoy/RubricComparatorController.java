package com.majordomo.adapter.in.web.envoy;

import com.majordomo.domain.model.envoy.RubricComparison;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.envoy.CompareRubricVersionsUseCase;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Renders the rubric A/B comparison page at
 * {@code /envoy/rubrics/{name}/compare?from=N&to=M&limit=L}. Compares two
 * versions of the named rubric over the most-recent {@code limit} postings;
 * results are in-memory only (no live {@code ScoreReport} is overwritten).
 */
@Controller
public class RubricComparatorController {

    /** Default and maximum number of postings to score against each version. */
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final CompareRubricVersionsUseCase comparisonUseCase;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    /** Resolved authentication context. */
    private record AuthContext(User user, UUID organizationId) { }

    /**
     * Constructs the controller.
     *
     * @param comparisonUseCase    inbound port for rubric comparison
     * @param userRepository       outbound port for user lookups
     * @param membershipRepository outbound port for membership lookups
     */
    public RubricComparatorController(CompareRubricVersionsUseCase comparisonUseCase,
                                      UserRepository userRepository,
                                      MembershipRepository membershipRepository) {
        this.comparisonUseCase = comparisonUseCase;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Renders the comparison view.
     *
     * @param name      rubric name (path variable)
     * @param from      older version (required)
     * @param to        newer version (required)
     * @param limit     posting set size (default 10, capped at 50)
     * @param principal authenticated user
     * @param model     Thymeleaf model
     * @return the {@code rubric-compare} template, or {@code redirect:/} if
     *         the user has no organization
     */
    @GetMapping("/envoy/rubrics/{name}/compare")
    public String compare(@PathVariable String name,
                          @RequestParam("from") int from,
                          @RequestParam("to") int to,
                          @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
                          @AuthenticationPrincipal UserDetails principal,
                          Model model) {
        AuthContext ctx = resolveContext(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        UUID orgId = ctx.organizationId();
        int clamped = Math.max(1, Math.min(limit, MAX_LIMIT));

        RubricComparison result = comparisonUseCase.compare(name, from, to, clamped, orgId);

        model.addAttribute("rubricName", name);
        model.addAttribute("fromVersion", from);
        model.addAttribute("toVersion", to);
        model.addAttribute("limit", clamped);
        model.addAttribute("result", result);
        model.addAttribute("organizationId", orgId);
        model.addAttribute("username", ctx.user().getUsername());
        return "rubric-compare";
    }

    private AuthContext resolveContext(UserDetails principal) {
        var user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        var memberships = membershipRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            return new AuthContext(user, null);
        }
        return new AuthContext(user, memberships.get(0).getOrganizationId());
    }
}
