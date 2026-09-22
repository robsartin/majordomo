package com.majordomo.adapter.out.llm;

import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.application.envoy.MaterialPromptBuilder;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmMaterialResponse;
import com.majordomo.domain.model.envoy.MaterialBrief;
import com.majordomo.domain.model.envoy.MaterialKind;
import com.majordomo.domain.model.envoy.Tone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Parsing the model's drafting response (#350).
 */
@ExtendWith(MockitoExtension.class)
class AnthropicMaterialAdapterTest {

    @Mock
    private AnthropicMaterialClient client;

    @Test
    void parsesTheDraftAndItsClaims() {
        givenModelReturns("""
                {"draft": "I led a team of 4 engineers.",
                 "claims": [{"claim": "Led a team", "source": "Led a team of 4 engineers"}]}
                """);

        LlmMaterialResponse response = adapter().generate(brief());

        assertThat(response.draft()).isEqualTo("I led a team of 4 engineers.");
        assertThat(response.claims()).singleElement()
                .satisfies(c -> assertThat(c.source()).isEqualTo("Led a team of 4 engineers"));
    }

    /**
     * A model returning a claim with no source is a malformed response, not a
     * grounding decision — the adapter keeps it as the null it was and lets the
     * service reject it with a message about grounding.
     */
    @Test
    void keepsAClaimWithNoSourceRatherThanDroppingIt() {
        givenModelReturns("""
                {"draft": "I am great.", "claims": [{"claim": "Greatness"}]}
                """);

        assertThat(adapter().generate(brief()).claims()).singleElement()
                .satisfies(c -> assertThat(c.source()).isNull());
    }

    @Test
    void absentClaims_readAsNoneRatherThanNull() {
        givenModelReturns("{\"draft\": \"Hello.\"}");

        assertThat(adapter().generate(brief()).claims()).isEmpty();
    }

    @Test
    void unparseableOutput_failsLoudlyWithWhatWasReturned() {
        givenModelReturns("Here is your letter! Dear hiring manager,");

        assertThatThrownBy(() -> adapter().generate(brief()))
                .isInstanceOf(LlmScoringException.class)
                .hasMessageContaining("Dear hiring manager");
    }

    private void givenModelReturns(String json) {
        when(client.sendWithUsage(anyString(), anyString()))
                .thenReturn(new AnthropicMessageClient.MessageResult(json, Optional.empty()));
    }

    private AnthropicMaterialAdapter adapter() {
        return new AnthropicMaterialAdapter(client, new MaterialPromptBuilder());
    }

    private static MaterialBrief brief() {
        JobPosting posting = new JobPosting();
        posting.setId(UUID.randomUUID());
        posting.setCompany("Globex");
        posting.setTitle("Staff Engineer");
        posting.setRawText("Java, Spring.");
        posting.setFetchedAt(Instant.now());
        return new MaterialBrief(posting, Optional.empty(),
                MaterialKind.COVER_LETTER, Tone.DIRECT, "Led a team of 4 engineers.");
    }
}
