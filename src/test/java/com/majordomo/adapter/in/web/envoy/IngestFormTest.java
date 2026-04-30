package com.majordomo.adapter.in.web.envoy;

import com.majordomo.domain.model.envoy.JobSourceRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link IngestForm}, the {@code @ModelAttribute}-bound DTO that
 * replaces the {@code @RequestParam} swarm on
 * {@link EnvoyPageController#submitIngest}.
 */
class IngestFormTest {

    @Test
    void typeDefaultsToManualWhenMissing() {
        IngestForm form = new IngestForm(null, "body", null, null, null);
        assertThat(form.type()).isEqualTo("manual");
    }

    @Test
    void typeDefaultsToManualWhenBlank() {
        IngestForm form = new IngestForm("   ", "body", null, null, null);
        assertThat(form.type()).isEqualTo("manual");
    }

    @Test
    void hasBlankPayloadIsTrueForNullEmptyOrWhitespace() {
        assertThat(new IngestForm("manual", null, null, null, null).hasBlankPayload()).isTrue();
        assertThat(new IngestForm("manual", "", null, null, null).hasBlankPayload()).isTrue();
        assertThat(new IngestForm("manual", "   ", null, null, null).hasBlankPayload()).isTrue();
        assertThat(new IngestForm("manual", "body", null, null, null).hasBlankPayload()).isFalse();
    }

    @Test
    void toRequestForwardsTypeAndPayload() {
        IngestForm form = new IngestForm("greenhouse", "https://boards.greenhouse.io/x/1",
                null, null, null);
        JobSourceRequest req = form.toRequest();
        assertThat(req.type()).isEqualTo("greenhouse");
        assertThat(req.payload()).isEqualTo("https://boards.greenhouse.io/x/1");
        assertThat(req.hints()).isEmpty();
    }

    @Test
    void toRequestIncludesAllNonBlankHintsTrimmed() {
        IngestForm form = new IngestForm("manual", "body",
                "  Acme  ", "Senior Backend Engineer", "Remote (US)");
        JobSourceRequest req = form.toRequest();
        assertThat(req.hints())
                .containsEntry("company", "Acme")
                .containsEntry("title", "Senior Backend Engineer")
                .containsEntry("location", "Remote (US)")
                .hasSize(3);
    }

    @Test
    void toRequestOmitsBlankHints() {
        IngestForm form = new IngestForm("manual", "body", "", "  ", null);
        JobSourceRequest req = form.toRequest();
        assertThat(req.hints()).isEmpty();
    }
}
