package com.majordomo.application.envoy;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.ApplicationMaterial;
import com.majordomo.domain.model.envoy.CitationVerifier;
import com.majordomo.domain.model.envoy.ClaimCitation;
import com.majordomo.domain.model.envoy.GroundingCheck;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmMaterialResponse;
import com.majordomo.domain.model.envoy.MaterialBrief;
import com.majordomo.domain.model.envoy.MaterialKind;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.Tone;
import com.majordomo.domain.port.in.envoy.GenerateApplicationMaterialUseCase;
import com.majordomo.domain.port.in.envoy.ResolveResumeUseCase;
import com.majordomo.domain.port.out.envoy.ApplicationMaterialRepository;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.LlmMaterialPort;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drafts application materials and refuses the ones that are not grounded
 * (#350, ADR-0028).
 *
 * <p>The order matters. The résumé is resolved first, so a missing one fails
 * before an API call is paid for. The draft is checked before it is stored, so
 * an ungrounded one never becomes a record of something that could be sent.
 */
@Service
public class ApplicationMaterialService implements GenerateApplicationMaterialUseCase {

    private final JobPostingRepository postings;
    private final ScoreReportRepository reports;
    private final ApplicationMaterialRepository materials;
    private final LlmMaterialPort llm;
    private final ResolveResumeUseCase resumes;

    /**
     * Constructs the service.
     *
     * @param postings  posting lookup
     * @param reports   score report lookup, for the rationale
     * @param materials draft storage
     * @param llm       the drafting port
     * @param resumes   the grounding source
     */
    public ApplicationMaterialService(
            JobPostingRepository postings,
            ScoreReportRepository reports,
            ApplicationMaterialRepository materials,
            LlmMaterialPort llm,
            ResolveResumeUseCase resumes) {
        this.postings = postings;
        this.reports = reports;
        this.materials = materials;
        this.llm = llm;
        this.resumes = resumes;
    }

    @Override
    public ApplicationMaterial generate(
            UUID postingId, MaterialKind kind, Tone tone, UUID userId, UUID organizationId) {

        JobPosting posting = postings.findById(postingId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("JobPosting", postingId));

        // Before the API call: no résumé means no grounded draft is possible,
        // and there is no point paying for one to find that out.
        String resumeText = resumes.resolve(userId);

        Optional<ScoreReport> report = reports.findLatestForPosting(postingId, organizationId);

        LlmMaterialResponse response = llm.generate(
                new MaterialBrief(posting, report, kind, tone, resumeText));

        List<String> problems = new ArrayList<>();
        List<ClaimCitation> claims = toCitations(response.claims(), problems);

        GroundingCheck check = CitationVerifier.check(
                response.draft(), claims, resumeText, posting.getRawText());
        problems.addAll(check.problems());

        if (!problems.isEmpty()) {
            throw new UngroundedDraftException(problems);
        }

        return materials.save(new ApplicationMaterial(
                UuidFactory.newId(), organizationId, postingId, report.map(ScoreReport::id),
                kind, tone, response.draft(), claims, llm.modelId(), Instant.now(),
                response.usage()));
    }

    /**
     * Converts the model's raw claims, collecting rather than throwing on the
     * invalid ones. A source span too short to be evidence is a grounding
     * failure; letting the constructor's exception escape would report a
     * malformed response for what is really an unsupported claim.
     */
    private static List<ClaimCitation> toCitations(
            List<LlmMaterialResponse.RawClaim> raw, List<String> problems) {
        List<ClaimCitation> citations = new ArrayList<>();
        for (LlmMaterialResponse.RawClaim claim : raw) {
            try {
                citations.add(new ClaimCitation(claim.claim(), claim.source()));
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        return citations;
    }
}
