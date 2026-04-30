package com.majordomo.adapter.in.web.envoy;

import com.majordomo.domain.model.envoy.JobSourceRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Form-backed DTO bound to the inline "Score a posting" form on {@code POST
 * /envoy}. Encapsulates the source discriminator, payload, and optional
 * extraction hints so {@link EnvoyPageController#submitIngest} no longer has
 * to spell out a {@code @RequestParam} swarm or hand-build the hint map.
 *
 * <p>The compact constructor normalises a missing or blank {@code type} to
 * {@code "manual"} so the controller doesn't need its own
 * {@code @RequestParam(defaultValue = "manual")} fallback.</p>
 *
 * @param type     source discriminator (defaults to {@code "manual"})
 * @param payload  raw posting text / URL / source-specific id
 * @param company  optional company hint
 * @param title    optional title hint
 * @param location optional location hint
 */
public record IngestForm(
        String type,
        String payload,
        String company,
        String title,
        String location) {

    /**
     * Normalises a missing or blank {@link #type()} to {@code "manual"} so the
     * controller can drop its own {@code @RequestParam(defaultValue = ...)}
     * fallback.
     */
    public IngestForm {
        if (type == null || type.isBlank()) {
            type = "manual";
        }
    }

    /**
     * @return {@code true} when {@link #payload()} is null, empty, or whitespace
     */
    public boolean hasBlankPayload() {
        return payload == null || payload.isBlank();
    }

    /**
     * @return a {@link JobSourceRequest} with the bound type, payload, and a
     *         hint map containing only the non-blank hints, trimmed
     */
    public JobSourceRequest toRequest() {
        Map<String, String> hints = new LinkedHashMap<>();
        putIfNotBlank(hints, "company", company);
        putIfNotBlank(hints, "title", title);
        putIfNotBlank(hints, "location", location);
        return new JobSourceRequest(type, payload, hints);
    }

    private static void putIfNotBlank(Map<String, String> hints, String key, String value) {
        if (value != null && !value.isBlank()) {
            hints.put(key, value.trim());
        }
    }
}
