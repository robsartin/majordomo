package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.RubricComparison;
import com.majordomo.domain.model.envoy.RubricComparison.PostingComparison;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.CompareRubricVersionsUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs rubric A/B comparison: re-scores the same posting set against two
 * versions of a rubric and aggregates the results. Reports produced here are
 * in-memory only — they do not pass through {@link ScoreReport#id()} assignment
 * or {@link com.majordomo.domain.port.out.envoy.ScoreReportRepository#save},
 * so the live report for each posting+rubric pair remains untouched.
 */
@Service
public class RubricComparisonService implements CompareRubricVersionsUseCase {

    /** Hard cap on the posting set; LLM calls are quadratic in (postings × versions). */
    private static final int MAX_POSTINGS = 50;

    private final RubricRepository rubrics;
    private final JobPostingRepository postings;
    private final LlmScoringPort llm;
    private final ScoreAssembler assembler;

    /**
     * Constructs the service.
     *
     * @param rubrics   outbound port for rubric lookups
     * @param postings  outbound port for postings
     * @param llm       outbound LLM scoring port
     * @param assembler deterministic LLM-response validator
     */
    public RubricComparisonService(RubricRepository rubrics,
                                   JobPostingRepository postings,
                                   LlmScoringPort llm,
                                   ScoreAssembler assembler) {
        this.rubrics = rubrics;
        this.postings = postings;
        this.llm = llm;
        this.assembler = assembler;
    }

    @Override
    public RubricComparison compare(String rubricName,
                                    int fromVersion,
                                    int toVersion,
                                    int limit,
                                    UUID organizationId) {
        if (fromVersion == toVersion) {
            throw new IllegalArgumentException(
                    "fromVersion and toVersion must differ");
        }
        Rubric fromRubric = rubrics.findByOrgNameVersion(rubricName, fromVersion, organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rubric version not found: " + rubricName + " v" + fromVersion));
        Rubric toRubric = rubrics.findByOrgNameVersion(rubricName, toVersion, organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rubric version not found: " + rubricName + " v" + toVersion));

        int clampedLimit = Math.max(1, Math.min(limit, MAX_POSTINGS));
        List<JobPosting> set = postings.findRecentByOrganizationId(organizationId, clampedLimit);

        String modelId = llm.modelId();
        List<PostingComparison> rows = new ArrayList<>(set.size());
        Map<Recommendation, Long> fromDist = new EnumMap<>(Recommendation.class);
        Map<Recommendation, Long> toDist = new EnumMap<>(Recommendation.class);
        long fromSum = 0;
        long toSum = 0;
        long flips = 0;

        for (JobPosting posting : set) {
            ScoreReport fromReport = scoreFresh(posting, fromRubric, modelId);
            ScoreReport toReport = scoreFresh(posting, toRubric, modelId);
            int delta = toReport.finalScore() - fromReport.finalScore();
            boolean flipped = fromReport.recommendation() != toReport.recommendation();
            if (flipped) {
                flips++;
            }
            rows.add(new PostingComparison(posting, fromReport, toReport, delta, flipped));
            fromDist.merge(fromReport.recommendation(), 1L, Long::sum);
            toDist.merge(toReport.recommendation(), 1L, Long::sum);
            fromSum += fromReport.finalScore();
            toSum += toReport.finalScore();
        }

        double meanFrom = set.isEmpty() ? 0.0 : ((double) fromSum) / set.size();
        double meanTo = set.isEmpty() ? 0.0 : ((double) toSum) / set.size();
        return new RubricComparison(
                rubricName, fromVersion, toVersion, rows,
                meanFrom, meanTo, fromDist, toDist, flips);
    }

    /**
     * Scores a single posting against a rubric and returns an in-memory
     * {@link ScoreReport} — never persisted, never publishes events, never
     * touches metrics. Identical assembly path as {@link JobScorerService} so
     * the resulting fields (categoryScores, finalScore, recommendation) are
     * directly comparable to live reports.
     */
    private ScoreReport scoreFresh(JobPosting posting, Rubric rubric, String modelId) {
        LlmScoreResponse resp = llm.score(posting, rubric);
        return assembler.assemble(posting, rubric, resp, modelId);
    }
}
