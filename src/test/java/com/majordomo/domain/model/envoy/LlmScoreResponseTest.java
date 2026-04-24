package com.majordomo.domain.model.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
