package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.Tier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds a {@link ScoringPrompt} from a rubric and posting. The rubric is
 * rendered without point values so the LLM cannot anchor on numbers when
 * choosing tiers.
 */
@Component
public class PromptBuilder {

    /**
     * Builds the scoring prompt for this posting + rubric pair.
     *
     * @param posting the posting to score
     * @param rubric  the active rubric
     * @return the system + user prompts for the LLM call
     */
    public ScoringPrompt build(JobPosting posting, Rubric rubric) {
        return new ScoringPrompt(renderSystemPrompt(rubric), renderUserPrompt(posting));
    }

    private String renderSystemPrompt(Rubric rubric) {
        var sb = new StringBuilder();
        sb.append("You are a job-posting scorer. You will read a job posting and ")
          .append("return a strict JSON object matching this schema:\n\n")
          .append("""
                  {
                    "disqualifierKey": string | null,
                    "categoryVerdicts": [
                      {"categoryKey": string, "tierLabel": string, "rationale": string}
                    ],
                    "flagHits": [
                      {"flagKey": string, "rationale": string}
                    ]
                  }
                  """)
          .append("\nReturn exactly one categoryVerdict per category below.\n")
          .append("If any disqualifier applies, set disqualifierKey to its key and ")
          .append("return empty categoryVerdicts and flagHits.\n\n");

        sb.append("## Disqualifiers (hard fails)\n");
        if (rubric.disqualifiers().isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (var d : rubric.disqualifiers()) {
                sb.append("- `").append(d.key()).append("`: ").append(d.description()).append("\n");
            }
        }

        sb.append("\n## Categories\n");
        for (Category c : rubric.categories()) {
            sb.append("\n### `").append(c.key()).append("` — ").append(c.description()).append("\n");
            sb.append("Pick ONE tier by label:\n");
            for (Tier t : c.tiers()) {
                sb.append("- \"").append(t.label()).append("\": ").append(t.criteria()).append("\n");
            }
        }

        sb.append("\n## Flags (soft, cumulative)\n");
        if (rubric.flags().isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (var f : rubric.flags()) {
                sb.append("- `").append(f.key()).append("`: ").append(f.description()).append("\n");
            }
        }

        sb.append("\nRespond with JSON only. No prose, no code fences.");
        return sb.toString();
    }

    private String renderUserPrompt(JobPosting posting) {
        var sb = new StringBuilder();
        sb.append("Company: ").append(nullSafe(posting.getCompany())).append("\n")
          .append("Title: ").append(nullSafe(posting.getTitle())).append("\n")
          .append("Location: ").append(nullSafe(posting.getLocation())).append("\n");
        Map<String, String> extracted = posting.getExtracted();
        if (extracted != null && !extracted.isEmpty()) {
            sb.append("Extracted fields:\n");
            sb.append(extracted.entrySet().stream()
                    .map(e -> "  - " + e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("\n")));
            sb.append("\n");
        }
        sb.append("\n--- POSTING ---\n").append(nullSafe(posting.getRawText()));
        return sb.toString();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
