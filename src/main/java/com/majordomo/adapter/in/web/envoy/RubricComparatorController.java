package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.domain.model.envoy.RubricComparison;
import com.majordomo.domain.port.in.envoy.CompareRubricVersionsUseCase;
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

    /**
     * Constructs the controller.
     *
     * @param comparisonUseCase inbound port for rubric comparison
     */
    public RubricComparatorController(CompareRubricVersionsUseCase comparisonUseCase) {
        this.comparisonUseCase = comparisonUseCase;
    }

    /**
     * Renders the comparison view.
     *
     * @param name       rubric name (path variable)
     * @param from       older version (required)
     * @param to         newer version (required)
     * @param limit      posting set size (default 10, capped at 50)
     * @param orgContext authenticated user + organization
     * @param model      Thymeleaf model
     * @return the {@code rubric-compare} template
     */
    @GetMapping("/envoy/rubrics/{name}/compare")
    public String compare(@PathVariable String name,
                          @RequestParam("from") int from,
                          @RequestParam("to") int to,
                          @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
                          OrgContext orgContext,
                          Model model) {
        UUID orgId = orgContext.organizationId();
        int clamped = Math.max(1, Math.min(limit, MAX_LIMIT));

        RubricComparison result = comparisonUseCase.compare(name, from, to, clamped, orgId);

        model.addAttribute("rubricName", name);
        model.addAttribute("fromVersion", from);
        model.addAttribute("toVersion", to);
        model.addAttribute("limit", clamped);
        model.addAttribute("result", result);
        model.addAttribute("organizationId", orgId);
        model.addAttribute("username", orgContext.username());
        return "rubric-compare";
    }
}
