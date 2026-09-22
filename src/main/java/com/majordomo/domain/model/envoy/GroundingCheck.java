package com.majordomo.domain.model.envoy;

import java.util.List;

/**
 * The outcome of checking a draft against its sources (#350, ADR-0028).
 *
 * @param problems every way the draft is not grounded, empty when it is
 */
public record GroundingCheck(List<String> problems) {

    /**
     * Copies the list so a check cannot change after it was made.
     */
    public GroundingCheck {
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    /**
     * Whether the draft is grounded in its sources.
     *
     * @return true when nothing was wrong
     */
    public boolean passed() {
        return problems.isEmpty();
    }
}
