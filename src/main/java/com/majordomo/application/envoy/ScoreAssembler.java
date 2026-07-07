package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.CategoryScore;
import com.majordomo.domain.model.envoy.Disqualifier;
import com.majordomo.domain.model.envoy.Flag;
import com.majordomo.domain.model.envoy.FlagHit;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.Tier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns an {@link LlmScoreResponse} into a validated, persisted-ready
 * {@link ScoreReport}. All validation is deterministic; any mismatch between the
 * LLM output and the rubric throws {@link LlmScoringException}.
 */
@Component
public class ScoreAssembler {

    /**
     * Validates {@code resp} against {@code rubric} and assembles a {@code ScoreReport}.
     * Does not persist.
     *
     * @param posting     the posting being scored
     * @param rubric      the rubric used to prompt the LLM
     * @param resp        the LLM's structured response
     * @param llmModel    model identifier to record on the report
     * @param contentHash fingerprint of the scored posting content, recorded so a
     *                    later unchanged re-score can reuse this report
     * @return a fully-validated report ready to persist
     */
    public ScoreReport assemble(JobPosting posting, Rubric rubric,
                                LlmScoreResponse resp, String llmModel, String contentHash) {
        return doAssemble(posting, rubric, resp, llmModel, contentHash);
    }

    /**
     * Assembles a report with no content fingerprint. For ephemeral uses such as
     * side-by-side rubric comparison, where the result is never persisted or reused
     * and so needs no idempotency key.
     *
     * @param posting  the posting being scored
     * @param rubric   the rubric used to prompt the LLM
     * @param resp     the LLM's structured response
     * @param llmModel model identifier to record on the report
     * @return a fully-validated report with an empty {@code contentHash}
     */
    public ScoreReport assemble(JobPosting posting, Rubric rubric,
                                LlmScoreResponse resp, String llmModel) {
        return doAssemble(posting, rubric, resp, llmModel, null);
    }

    private ScoreReport doAssemble(JobPosting posting, Rubric rubric,
                                   LlmScoreResponse resp, String llmModel, String contentHash) {

        if (resp.disqualifierKey().isPresent()) {
            Disqualifier dq = lookupDisqualifier(rubric, resp.disqualifierKey().get());
            return new ScoreReport(
                    UuidFactory.newId(),
                    posting.getOrganizationId(),
                    posting.getId(),
                    rubric.id(),
                    rubric.version(),
                    Optional.of(dq),
                    List.of(),
                    List.of(),
                    0, 0,
                    Recommendation.SKIP,
                    llmModel,
                    Instant.now(),
                    resp.usage(),
                    Optional.ofNullable(contentHash));
        }

        List<CategoryScore> categoryScores = new ArrayList<>();
        int rawScore = 0;
        for (var verdict : resp.categoryVerdicts()) {
            Tier tier = lookupTier(rubric, verdict.categoryKey(), verdict.tierLabel());
            categoryScores.add(new CategoryScore(
                    verdict.categoryKey(),
                    tier.points(),
                    tier.label(),
                    verdict.rationale(),
                    verdict.confidence()));
            rawScore += tier.points();
        }

        requireAllCategoriesCovered(rubric, resp);

        List<FlagHit> flagHits = new ArrayList<>();
        int totalPenalty = 0;
        for (var finding : resp.flagHits()) {
            Flag flag = lookupFlag(rubric, finding.flagKey());
            flagHits.add(new FlagHit(flag.key(), flag.penalty(), finding.rationale()));
            totalPenalty += flag.penalty();
        }

        int finalScore = Math.max(0, rawScore - totalPenalty);
        Recommendation recommendation = Recommendation.fromScore(finalScore, rubric.thresholds());

        return new ScoreReport(
                UuidFactory.newId(),
                posting.getOrganizationId(),
                posting.getId(),
                rubric.id(),
                rubric.version(),
                Optional.empty(),
                List.copyOf(categoryScores),
                List.copyOf(flagHits),
                rawScore,
                finalScore,
                recommendation,
                llmModel,
                Instant.now(),
                resp.usage(),
                Optional.ofNullable(contentHash));
    }

    private Disqualifier lookupDisqualifier(Rubric rubric, String key) {
        return rubric.disqualifiers().stream()
                .filter(d -> d.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new LlmScoringException(
                        "LLM returned unknown disqualifier key: " + key));
    }

    private Tier lookupTier(Rubric rubric, String categoryKey, String tierLabel) {
        var cat = rubric.categories().stream()
                .filter(c -> c.key().equals(categoryKey))
                .findFirst()
                .orElseThrow(() -> new LlmScoringException(
                        "LLM returned unknown category key: " + categoryKey));
        return cat.tiers().stream()
                .filter(t -> t.label().equals(tierLabel))
                .findFirst()
                .orElseThrow(() -> new LlmScoringException(
                        "LLM returned unknown tier label '" + tierLabel
                                + "' for category '" + categoryKey + "'"));
    }

    private Flag lookupFlag(Rubric rubric, String flagKey) {
        return rubric.flags().stream()
                .filter(f -> f.key().equals(flagKey))
                .findFirst()
                .orElseThrow(() -> new LlmScoringException(
                        "LLM returned unknown flag key: " + flagKey));
    }

    private void requireAllCategoriesCovered(Rubric rubric, LlmScoreResponse resp) {
        var coveredKeys = resp.categoryVerdicts().stream()
                .map(LlmScoreResponse.CategoryVerdict::categoryKey)
                .toList();
        for (var cat : rubric.categories()) {
            if (!coveredKeys.contains(cat.key())) {
                throw new LlmScoringException(
                        "LLM response missing required category: " + cat.key());
            }
        }
    }

}
