package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.CategoryScore;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Thymeleaf controller for the Envoy posting comparator. Renders a side-by-side
 * view of 2&ndash;5 scored postings under a single rubric, surfacing where the
 * postings agree, where they diverge, and which are closest to or above the
 * apply threshold.
 *
 * <p>Postings whose latest report is against a different rubric are shown as
 * "not scored" columns rather than dropped, so the user keeps full context for
 * the URL they bookmarked.</p>
 */
@Controller
public class EnvoyComparatorController {

    private static final int MIN_IDS = 2;
    private static final int MAX_IDS = 5;
    private static final String DEFAULT_RUBRIC = "default";

    private final QueryScoreReportsUseCase reports;
    private final RubricRepository rubricRepository;
    private final JobPostingRepository jobPostingRepository;

    /**
     * One column in the comparator table — a posting plus (optionally) the
     * report for the requested rubric.
     *
     * @param reportId               the requested report id from the {@code ids} param
     * @param report                 the matching {@link ScoreReport} when present and
     *                               scored against the requested rubric; {@code null} otherwise
     * @param posting                the source posting metadata; {@code null} if the
     *                               posting was deleted
     * @param notScored              {@code true} when the posting has no report for the
     *                               requested rubric (column is dimmed in the UI)
     * @param gapFromApplyThreshold  signed gap from the rubric's APPLY threshold
     *                               ({@code finalScore - thresholds.apply()}); always 0
     *                               when {@link #notScored()} is true
     */
    public record Column(
            UUID reportId,
            ScoreReport report,
            JobPosting posting,
            boolean notScored,
            int gapFromApplyThreshold) { }

    /**
     * One cell within a category row in the comparator table.
     *
     * @param tierLabel  selected tier label from the report; {@code null} when the
     *                   column is not scored or the report didn't score this category
     * @param points     points awarded; 0 when missing
     * @param isHighest  {@code true} when this cell ties or beats every other scored
     *                   cell in the row (gets the highlight class in the template)
     * @param scored     {@code false} when no score was found for this column/category
     */
    public record Cell(String tierLabel, int points, boolean isHighest, boolean scored) { }

    /**
     * One row in the comparator table — one entry per rubric category, in
     * rubric order, with one {@link Cell} per column.
     *
     * @param categoryKey the {@link Category#key()}
     * @param cells       cells aligned to the column ordering
     */
    public record Row(String categoryKey, List<Cell> cells) { }

    /**
     * Constructs the controller.
     *
     * @param reports              inbound port for report queries
     * @param rubricRepository     outbound port for rubric lookups
     * @param jobPostingRepository outbound port for posting lookups
     */
    public EnvoyComparatorController(QueryScoreReportsUseCase reports,
                                     RubricRepository rubricRepository,
                                     JobPostingRepository jobPostingRepository) {
        this.reports = reports;
        this.rubricRepository = rubricRepository;
        this.jobPostingRepository = jobPostingRepository;
    }

    /**
     * Renders the comparator page for the given {@code ids} (2&ndash;5 report
     * ids, comma-separated) under the named {@code rubric} (defaults to
     * {@code default}).
     *
     * <p>Behaviours:
     * <ul>
     *   <li>Returns 400 when fewer than 2 or more than 5 ids are supplied.</li>
     *   <li>Returns 400 when the requested rubric does not exist for the org
     *       (handled by the global {@code IllegalArgumentException} mapper).</li>
     *   <li>Redirects to {@code /} when the user has no organization membership.</li>
     *   <li>Reports against a different rubric show as "not scored" columns
     *       (the user still sees the company / title / location).</li>
     * </ul>
     *
     * @param idsParam   comma-separated report ids
     * @param rubric     rubric name to compare against (default {@code default})
     * @param orgContext authenticated user + organization
     * @param model      Thymeleaf model
     * @return the {@code envoy-compare} template name
     */
    @GetMapping("/envoy/compare")
    public String compare(@RequestParam(name = "ids") String idsParam,
                          @RequestParam(name = "rubric", defaultValue = DEFAULT_RUBRIC)
                          String rubric,
                          OrgContext orgContext,
                          Model model) {
        List<UUID> ids = parseIds(idsParam);
        UUID orgId = orgContext.organizationId();

        Rubric activeRubric = rubricRepository.findActiveByName(rubric, orgId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rubric not found: " + rubric));

        List<Column> columns = new ArrayList<>(ids.size());
        for (UUID reportId : ids) {
            columns.add(buildColumn(reportId, orgId, activeRubric));
        }

        List<Row> rows = buildRows(activeRubric, columns);

        model.addAttribute("rubric", activeRubric);
        model.addAttribute("rubricName", rubric);
        model.addAttribute("columns", columns);
        model.addAttribute("rows", rows);
        model.addAttribute("organizationId", orgId);
        model.addAttribute("username", orgContext.username());
        return "envoy-compare";
    }

    private List<UUID> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("ids is required");
        }
        // De-duplicate while preserving order.
        var uniq = new LinkedHashSet<UUID>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                uniq.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid UUID: " + trimmed);
            }
        }
        if (uniq.size() < MIN_IDS) {
            throw new IllegalArgumentException(
                    "At least " + MIN_IDS + " ids are required");
        }
        if (uniq.size() > MAX_IDS) {
            throw new IllegalArgumentException(
                    "At most " + MAX_IDS + " ids are allowed");
        }
        return new ArrayList<>(uniq);
    }

    private Column buildColumn(UUID reportId, UUID orgId, Rubric activeRubric) {
        Optional<ScoreReport> reportOpt = reports.findById(reportId, orgId);
        if (reportOpt.isEmpty()) {
            // Unknown id — treat as not scored so the page still renders.
            return new Column(reportId, null, null, true, 0);
        }
        ScoreReport report = reportOpt.get();
        JobPosting posting = jobPostingRepository.findById(report.postingId(), orgId)
                .orElse(null);

        boolean sameRubric = report.rubricId().equals(activeRubric.id());
        if (!sameRubric) {
            return new Column(reportId, null, posting, true, 0);
        }
        int gap = report.finalScore() - activeRubric.thresholds().apply();
        return new Column(reportId, report, posting, false, gap);
    }

    private List<Row> buildRows(Rubric rubric, List<Column> columns) {
        List<Row> rows = new ArrayList<>(rubric.categories().size());
        for (Category category : rubric.categories()) {
            List<Cell> cells = new ArrayList<>(columns.size());
            int max = Integer.MIN_VALUE;
            for (Column col : columns) {
                ScoreReport r = col.report();
                if (r == null) {
                    cells.add(new Cell(null, 0, false, false));
                    continue;
                }
                Optional<CategoryScore> match = r.categoryScores().stream()
                        .filter(cs -> cs.categoryKey().equals(category.key()))
                        .findFirst();
                if (match.isEmpty()) {
                    cells.add(new Cell(null, 0, false, false));
                    continue;
                }
                CategoryScore cs = match.get();
                cells.add(new Cell(cs.tierLabel(), cs.points(), false, true));
                if (cs.points() > max) {
                    max = cs.points();
                }
            }
            // Mark every scored cell whose points equal the row max as highest.
            // If no cells were scored, no highlight.
            if (max != Integer.MIN_VALUE) {
                List<Cell> highlighted = new ArrayList<>(cells.size());
                for (Cell c : cells) {
                    if (c.scored() && c.points() == max) {
                        highlighted.add(new Cell(c.tierLabel(), c.points(), true, true));
                    } else {
                        highlighted.add(c);
                    }
                }
                cells = highlighted;
            }
            rows.add(new Row(category.key(), cells));
        }
        return rows;
    }

}
