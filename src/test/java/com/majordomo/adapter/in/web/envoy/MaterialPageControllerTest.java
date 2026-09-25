package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.application.envoy.ResumeNotAvailableException;
import com.majordomo.application.envoy.UngroundedDraftException;
import com.majordomo.domain.model.envoy.ApplicationMaterial;
import com.majordomo.domain.model.envoy.MaterialKind;
import com.majordomo.domain.model.envoy.Tone;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.envoy.GenerateApplicationMaterialUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Generating a draft from the report page (#352).
 *
 * <p>The failures matter more than the success here. Generation refuses more
 * often than most actions — no résumé, not read yet, a claim that could not be
 * traced — and each refusal has a different fix, so the page has to say which
 * one happened rather than "generation failed".
 */
@ExtendWith(MockitoExtension.class)
class MaterialPageControllerTest {

    private static final UUID REPORT = UUID.randomUUID();
    private static final UUID POSTING = UUID.randomUUID();
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private GenerateApplicationMaterialUseCase generate;

    private MaterialPageController controller;

    @BeforeEach
    void setUp() {
        controller = new MaterialPageController(generate);
    }

    @Test
    void generate_returnsToTheReportPage() {
        when(generate.generate(eq(POSTING), any(), any(), eq(USER_ID), eq(ORG)))
                .thenReturn(material());
        var flash = new RedirectAttributesModelMap();

        String view = controller.generate(
                REPORT, POSTING, MaterialKind.COVER_LETTER, Tone.DIRECT, orgContext(), flash);

        assertThat(view).isEqualTo("redirect:/envoy/reports/" + REPORT);
        assertThat(flash.getFlashAttributes()).doesNotContainKey("materialError");
    }

    /**
     * An ungrounded draft is discarded, so the page has nothing to show. It has
     * to say what could not be traced — the whole point of the guard is that a
     * person gets to see why.
     */
    @Test
    void ungroundedDraft_reportsWhatCouldNotBeTraced() {
        when(generate.generate(any(), any(), any(), any(), any()))
                .thenThrow(new UngroundedDraftException(
                        List.of("Cited source is not in the résumé: \"Director at Initech\"")));
        var flash = new RedirectAttributesModelMap();

        controller.generate(
                REPORT, POSTING, MaterialKind.COVER_LETTER, Tone.DIRECT, orgContext(), flash);

        assertThat(flash.getFlashAttributes().get("materialError").toString())
                .contains("Initech");
    }

    /** "Not read yet" and "none uploaded" need different answers, not one message. */
    @Test
    void missingResume_passesTheSpecificReasonThrough() {
        when(generate.generate(any(), any(), any(), any(), any()))
                .thenThrow(new ResumeNotAvailableException(
                        ResumeNotAvailableException.Reason.NOT_YET_EXTRACTED));
        var flash = new RedirectAttributesModelMap();

        controller.generate(
                REPORT, POSTING, MaterialKind.INTRO_MESSAGE, Tone.WARM, orgContext(), flash);

        assertThat(flash.getFlashAttributes().get("materialError").toString())
                .contains("has not been read yet");
    }

    private static OrgContext orgContext() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("robsartin");
        return new OrgContext(user, ORG);
    }

    private static ApplicationMaterial material() {
        return new ApplicationMaterial(
                UUID.randomUUID(), ORG, POSTING, Optional.of(REPORT),
                MaterialKind.COVER_LETTER, Tone.DIRECT, "Dear hiring manager",
                List.of(), "claude-opus-5", Instant.now(), Optional.empty());
    }
}
