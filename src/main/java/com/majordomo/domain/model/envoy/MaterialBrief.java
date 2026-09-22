package com.majordomo.domain.model.envoy;

import java.util.Optional;

/**
 * Everything a draft is written from (#350, ADR-0028).
 *
 * @param posting    the role being applied for
 * @param report     the score report whose rationale explains why this role
 *                   suits, when the posting has been scored
 * @param kind       what to draft
 * @param tone       the register
 * @param resumeText the user's résumé — the only source for claims about them
 */
public record MaterialBrief(
        JobPosting posting,
        Optional<ScoreReport> report,
        MaterialKind kind,
        Tone tone,
        String resumeText
) { }
