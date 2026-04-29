package com.majordomo.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JsonColumnCodec}.
 */
class JsonColumnCodecTest {

    /** A round-trip through encode/decode preserves a record's fields. */
    @Test
    void roundTripsRecord() {
        Sample original = new Sample("alice", 42, Instant.parse("2026-04-29T12:00:00Z"));

        String json = JsonColumnCodec.encode(original, "Sample");
        Sample decoded = JsonColumnCodec.decode(json, Sample.class, "Sample");

        assertThat(decoded).isEqualTo(original);
    }

    /** TypeReference path round-trips a generic Map. */
    @Test
    void roundTripsTypeReference() {
        Map<String, String> original = Map.of("key", "value", "k2", "v2");
        String json = JsonColumnCodec.encode(original, "Map");

        Map<String, String> decoded = JsonColumnCodec.decode(
                json, new TypeReference<Map<String, String>>() { }, "Map");

        assertThat(decoded).isEqualTo(original);
    }

    /** Optional fields survive a round-trip thanks to Jdk8Module. */
    @Test
    void roundTripsOptional() {
        WithOptional original = new WithOptional(Optional.of("present"));
        String json = JsonColumnCodec.encode(original, "WithOptional");

        WithOptional decoded = JsonColumnCodec.decode(
                json, WithOptional.class, "WithOptional");

        assertThat(decoded.value()).contains("present");
    }

    /** java.time.Instant survives a round-trip thanks to JavaTimeModule. */
    @Test
    void roundTripsInstant() {
        WithInstant original = new WithInstant(Instant.parse("2026-04-29T12:00:00Z"));
        String json = JsonColumnCodec.encode(original, "WithInstant");

        WithInstant decoded = JsonColumnCodec.decode(
                json, WithInstant.class, "WithInstant");

        assertThat(decoded.when()).isEqualTo(original.when());
    }

    /** encode(null, ...) returns null without throwing. */
    @Test
    void encodeReturnsNullForNullValue() {
        assertThat(JsonColumnCodec.encode(null, "Anything")).isNull();
    }

    /** decode(null, ...) returns null without throwing. */
    @Test
    void decodeReturnsNullForNullJson() {
        assertThat(JsonColumnCodec.decode(null, Sample.class, "x")).isNull();
        assertThat(JsonColumnCodec.decode(
                null, new TypeReference<List<String>>() { }, "x")).isNull();
    }

    /** decode of malformed JSON wraps Jackson's exception as IllegalStateException. */
    @Test
    void decodeWrapsJacksonErrors() {
        assertThatThrownBy(() -> JsonColumnCodec.decode("not-json", Sample.class, "Sample"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sample");
    }

    record Sample(String name, int count, Instant when) { }
    record WithOptional(Optional<String> value) { }
    record WithInstant(Instant when) { }
}
