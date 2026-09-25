package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.application.envoy.ResumeNotAvailableException;
import com.majordomo.application.envoy.UngroundedDraftException;
import com.majordomo.domain.model.envoy.MaterialKind;
import com.majordomo.domain.model.envoy.Tone;
import com.majordomo.domain.port.in.envoy.GenerateApplicationMaterialUseCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Drafting an application material from a score report's page (#352,
 * ADR-0028).
 *
 * <p>Separate from {@code EnvoyPageController} to keep one responsibility
 * apiece; the report page itself lists the drafts that already exist.
 */
@Controller
public class MaterialPageController {

    private static final Logger LOG = LoggerFactory.getLogger(MaterialPageController.class);

    private final GenerateApplicationMaterialUseCase generate;

    /**
     * Constructs the controller.
     *
     * @param generate the drafting use case
     */
    public MaterialPageController(GenerateApplicationMaterialUseCase generate) {
        this.generate = generate;
    }

    /**
     * Drafts one material and returns to the report page.
     *
     * <p>Both failures are reported with the message the domain gave, not a
     * generic one. Generation refuses more often than most actions — no résumé,
     * one not read yet, a claim that could not be traced — and each of those has
     * a different fix. "Generation failed" would hide which.
     *
     * @param reportId   the report whose page this came from
     * @param postingId  the posting to write for
     * @param kind       what to draft
     * @param tone       the register
     * @param orgContext authenticated user and organization
     * @param flash      carries any error across the redirect
     * @return a redirect back to the report page
     */
    @PostMapping("/envoy/reports/{reportId}/materials")
    public String generate(@PathVariable UUID reportId,
                           @RequestParam("postingId") UUID postingId,
                           @RequestParam("kind") MaterialKind kind,
                           @RequestParam("tone") Tone tone,
                           OrgContext orgContext,
                           RedirectAttributes flash) {
        try {
            generate.generate(postingId, kind, tone,
                    orgContext.user().getId(), orgContext.organizationId());
        } catch (UngroundedDraftException e) {
            // The draft was discarded, so there is nothing to show. What the
            // page can show is why, which is the point of recording citations
            // at all.
            flash.addFlashAttribute("materialError",
                    "The draft was rejected because it said something your résumé does "
                            + "not support: " + String.join("; ", e.problems()));
        } catch (ResumeNotAvailableException e) {
            flash.addFlashAttribute("materialError", e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("Material generation failed for posting {}: {}", postingId, e.toString());
            flash.addFlashAttribute("materialError",
                    "Generation failed: " + e.getMessage());
        }
        return "redirect:/envoy/reports/" + reportId;
    }
}
