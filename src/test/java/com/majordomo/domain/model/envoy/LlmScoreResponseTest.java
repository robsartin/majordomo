package com.majordomo.domain.model.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmScoreResponseTest {

    @Test
    void deserialisesFromCanonicalJson() throws Exception {
        String json = """
                {
                  "disqualifierKey": null,
                  "categoryVerdicts": [
                    {"categoryKey": "compensation", "tierLabel": "Good",
                     "rationale": "Posting lists $200-250k base."}
                  ],
                  "flagHits": [
                    {"flagKey": "AT_WILL", "rationale": "explicit at-will clause"}
                  ]
                }
                """;
        var mapper = new ObjectMapper();
        var resp = mapper.readValue(json, LlmScoreResponse.class);

        assertThat(resp.disqualifierKey()).isEmpty();
        assertThat(resp.categoryVerdicts()).hasSize(1);
        assertThat(resp.categoryVerdicts().get(0).tierLabel()).isEqualTo("Good");
        assertThat(resp.flagHits()).hasSize(1);
    }

    @Test
    void deserialisesWithDisqualifier() throws Exception {
        String json = """
                {
                  "disqualifierKey": "ON_SITE_ONLY",
                  "categoryVerdicts": [],
                  "flagHits": []
                }
                """;
        var resp = new ObjectMapper().readValue(json, LlmScoreResponse.class);
        assertThat(resp.disqualifierKey()).contains("ON_SITE_ONLY");
    }

    @Test
    void deserialisesConfidenceFromCategoryVerdict() throws Exception {
        String json = """
                {
                  "disqualifierKey": null,
                  "categoryVerdicts": [
                    {"categoryKey": "compensation", "tierLabel": "Good",
                     "rationale": "Posting lists $200-250k base.",
                     "confidence": "HIGH"}
                  ],
                  "flagHits": []
                }
                """;
        var resp = new ObjectMapper().readValue(json, LlmScoreResponse.class);

        assertThat(resp.categoryVerdicts()).hasSize(1);
        assertThat(resp.categoryVerdicts().get(0).confidence())
                .contains(Confidence.HIGH);
    }

    @Test
    void categoryVerdictWithoutConfidenceDeserialisesAsEmpty() throws Exception {
        String json = """
                {
                  "disqualifierKey": null,
                  "categoryVerdicts": [
                    {"categoryKey": "compensation", "tierLabel": "Good",
                     "rationale": "old payload, no confidence field"}
                  ],
                  "flagHits": []
                }
                """;
        var resp = new ObjectMapper().readValue(json, LlmScoreResponse.class);

        assertThat(resp.categoryVerdicts()).hasSize(1);
        assertThat(resp.categoryVerdicts().get(0).confidence()).isEmpty();
    }

    @Test
    void invalidConfidenceStringIsRejected() {
        String json = """
                {
                  "disqualifierKey": null,
                  "categoryVerdicts": [
                    {"categoryKey": "compensation", "tierLabel": "Good",
                     "rationale": "r", "confidence": "VERY_HIGH"}
                  ],
                  "flagHits": []
                }
                """;
        assertThatThrownBy(() -> new ObjectMapper().readValue(json, LlmScoreResponse.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
    }

    @Test
    void allConfidenceValuesRoundTripThroughJackson() throws Exception {
        var mapper = new ObjectMapper();
        for (Confidence value : Confidence.values()) {
            String json = """
                    {
                      "disqualifierKey": null,
                      "categoryVerdicts": [
                        {"categoryKey": "compensation", "tierLabel": "Good",
                         "rationale": "r", "confidence": "%s"}
                      ],
                      "flagHits": []
                    }
                    """.formatted(value.name());
            var resp = mapper.readValue(json, LlmScoreResponse.class);
            assertThat(resp.categoryVerdicts().get(0).confidence()).contains(value);
        }
    }
}
