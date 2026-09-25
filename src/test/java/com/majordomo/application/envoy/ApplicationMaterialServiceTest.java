package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.ApplicationMaterial;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmMaterialResponse;
import com.majordomo.domain.model.envoy.MaterialBrief;
import com.majordomo.domain.model.envoy.MaterialKind;
import com.majordomo.domain.model.envoy.Tone;
import com.majordomo.domain.port.in.envoy.ResolveResumeUseCase;
import com.majordomo.domain.port.out.envoy.ApplicationMaterialRepository;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.LlmMaterialPort;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drafting a material end to end, with the LLM stubbed (#350, ADR-0028).
 */
@ExtendWith(MockitoExtension.class)
class ApplicationMaterialServiceTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID POSTING = UUID.randomUUID();

    private static final String RESUME = """
            Staff Software Engineer at Acme Corp.
            Led a team of 4 engineers. Java, Spring Boot, PostgreSQL.
            """;

    @Mock private JobPostingRepository postings;
    @Mock private ScoreReportRepository reports;
    @Mock private ApplicationMaterialRepository materials;
    @Mock private LlmMaterialPort llm;
    @Mock private ResolveResumeUseCase resumes;

    private ApplicationMaterialService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationMaterialService(postings, reports, materials, llm, resumes);
    }

    @Test
    void generate_storesAGroundedDraftWithItsClaims() {
        givenPosting();
        when(resumes.resolve(USER)).thenReturn(RESUME);
        when(llm.modelId()).thenReturn("claude-opus-5");
        when(llm.generate(any())).thenReturn(response(
                "I led a team of 4 engineers in Java.",
                "Led a team", "Led a team of 4 engineers"));
        when(materials.save(any())).thenAnswer(call -> call.getArgument(0));

        ApplicationMaterial saved = service.generate(
                POSTING, MaterialKind.COVER_LETTER, Tone.DIRECT, USER, ORG);

        assertThat(saved.content()).contains("team of 4 engineers");
        assertThat(saved.claims()).singleElement()
                .satisfies(claim -> assertThat(claim.source()).isEqualTo("Led a team of 4 engineers"));
        assertThat(saved.llmModel()).isEqualTo("claude-opus-5");
        verify(materials).save(any());
    }

    /**
     * The point of the whole issue. A draft whose claim cannot be traced to the
     * résumé is not stored, not returned with a warning, and not shown — it
     * fails, because a warning on a document someone is about to send is a note
     * that gets scrolled past.
     */
    @Test
    void generate_refusesADraftWhoseClaimIsNotInTheResume() {
        givenPosting();
        when(resumes.resolve(USER)).thenReturn(RESUME);
        when(llm.generate(any())).thenReturn(response(
                "I was Director of Engineering at Initech.",
                "Director at Initech", "Director of Engineering, Initech"));

        assertThatThrownBy(() -> service.generate(
                POSTING, MaterialKind.COVER_LETTER, Tone.DIRECT, USER, ORG))
                .isInstanceOf(UngroundedDraftException.class)
                .hasMessageContaining("Initech");

        verify(materials, never()).save(any());
    }

    /**
     * A source span too short to be evidence is a grounding failure, not a
     * parsing one. Letting {@code ClaimCitation}'s validation escape would
     * report a malformed response for what is really an unsupported claim.
     */
    @Test
    void generate_treatsATrivialCitationAsUngroundedRatherThanUnparseable() {
        givenPosting();
        when(resumes.resolve(USER)).thenReturn(RESUME);
        when(llm.generate(any())).thenReturn(response(
                "I know Java.", "Knows Java", "Java"));

        assertThatThrownBy(() -> service.generate(
                POSTING, MaterialKind.COVER_LETTER, Tone.DIRECT, USER, ORG))
                .isInstanceOf(UngroundedDraftException.class)
                .hasMessageContaining("too short");

        verify(materials, never()).save(any());
    }

    /** No résumé means no generation at all; the failure comes through unchanged. */
    @Test
    void generate_doesNotCallTheModelWithoutAResume() {
        givenPosting();
        when(resumes.resolve(USER)).thenThrow(new ResumeNotAvailableException(
                ResumeNotAvailableException.Reason.NONE_UPLOADED));

        assertThatThrownBy(() -> service.generate(
                POSTING, MaterialKind.COVER_LETTER, Tone.DIRECT, USER, ORG))
                .isInstanceOf(ResumeNotAvailableException.class);

        verify(llm, never()).generate(any());
    }

    /** The brief must carry the résumé, or the model drafts from nothing. */
    @Test
    void generate_passesTheResumeAndToneToTheModel() {
        givenPosting();
        when(resumes.resolve(USER)).thenReturn(RESUME);
        when(llm.modelId()).thenReturn("claude-opus-5");
        when(llm.generate(any())).thenReturn(response("Hello.", null, null));
        when(materials.save(any())).thenAnswer(call -> call.getArgument(0));

        service.generate(POSTING, MaterialKind.INTRO_MESSAGE, Tone.WARM, USER, ORG);

        var brief = org.mockito.ArgumentCaptor.forClass(MaterialBrief.class);
        verify(llm).generate(brief.capture());
        assertThat(brief.getValue().resumeText()).isEqualTo(RESUME);
        assertThat(brief.getValue().tone()).isEqualTo(Tone.WARM);
        assertThat(brief.getValue().kind()).isEqualTo(MaterialKind.INTRO_MESSAGE);
    }

    /**
     * Every kind goes through the same service, guard and storage — the only
     * thing that differs is the prompt. Asserting that rather than assuming it,
     * because "it is kind-agnostic" is exactly the sort of claim that stops
     * being true the first time someone special-cases one.
     */
    @Test
    void generate_handlesEveryKindThroughTheSamePath() {
        givenPosting();
        when(resumes.resolve(USER)).thenReturn(RESUME);
        when(llm.modelId()).thenReturn("claude-opus-5");
        when(llm.generate(any())).thenReturn(response(
                "I led a team of 4 engineers.",
                "Led a team", "Led a team of 4 engineers"));
        when(materials.save(any())).thenAnswer(call -> call.getArgument(0));

        for (MaterialKind kind : MaterialKind.values()) {
            assertThat(service.generate(POSTING, kind, Tone.DIRECT, USER, ORG).kind())
                    .isEqualTo(kind);
        }
    }

    /**
     * The defect #351 singles out for résumé bullets, at the level someone
     * would actually meet it. The rewrite keeps a real cited bullet and changes
     * its number to another number that does appear in the résumé — so it is
     * caught only because each claim's numbers are checked against its own
     * cited span.
     */
    @Test
    void generate_refusesARewriteThatChangesTheNumberInTheBulletItCites() {
        givenPosting();
        when(resumes.resolve(USER)).thenReturn(RESUME + "\nDelivered 12 projects.\n");
        when(llm.generate(any())).thenReturn(response(
                "Led a team of 12 engineers on the payments platform.",
                "Led a team of 12 engineers", "Led a team of 4 engineers"));

        assertThatThrownBy(() -> service.generate(
                POSTING, MaterialKind.RESUME_BULLETS, Tone.DIRECT, USER, ORG))
                .isInstanceOf(UngroundedDraftException.class)
                .hasMessageContaining("12");

        verify(materials, never()).save(any());
    }

    private void givenPosting() {
        JobPosting posting = new JobPosting();
        posting.setId(POSTING);
        posting.setOrganizationId(ORG);
        posting.setCompany("Globex");
        posting.setTitle("Staff Engineer");
        posting.setRawText("Java, Spring, remote.");
        posting.setFetchedAt(Instant.now());
        when(postings.findById(POSTING, ORG)).thenReturn(Optional.of(posting));
    }

    private static LlmMaterialResponse response(String draft, String claim, String source) {
        List<LlmMaterialResponse.RawClaim> claims = claim == null
                ? List.of()
                : List.of(new LlmMaterialResponse.RawClaim(claim, source));
        return new LlmMaterialResponse(draft, claims, Optional.empty());
    }
}
