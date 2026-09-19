package com.majordomo.adapter.out.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for how the engine runs a subprocess (#341).
 *
 * <p>The binary is a script written by the test. What is under test is our
 * handling of it — arguments, captured output, exit status, a process that
 * never returns — none of which is Tesseract's behaviour to define. That the
 * real binary is present and produces sensible text is checked against the
 * shipped image instead, and no machine running these tests needs it
 * installed.
 */
class TesseractProcessOcrEngineTest {

    @TempDir
    private Path tempDir;

    @Test
    void returnsWhatTheBinaryPrints() throws Exception {
        var engine = engine(script("#!/bin/sh\necho 'TOTAL 42.10'\n"), 10);

        assertThat(engine.recognise(new byte[] {1, 2, 3})).contains("TOTAL 42.10");
    }

    /** The image has to reach the binary, or it would recognise an empty file. */
    @Test
    void passesTheImageToTheBinary() throws Exception {
        var engine = engine(script("#!/bin/sh\nwc -c < \"$1\"\n"), 10);

        assertThat(engine.recognise(new byte[512]).trim()).isEqualTo("512");
    }

    /**
     * A failing engine must say why. Swallowing the binary's own message would
     * leave "OCR failed" as the only evidence, which is the report that makes a
     * problem take an afternoon instead of a minute.
     */
    @Test
    void nonZeroExit_throwsCarryingTheBinarysError() throws Exception {
        var engine = engine(script("#!/bin/sh\necho 'Error in pixReadStream' >&2\nexit 1\n"), 10);

        assertThatThrownBy(() -> engine.recognise(new byte[] {1}))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("pixReadStream");
    }

    /**
     * Tesseract can sit on a pathological image indefinitely. Without a timeout
     * that process holds a thread of the extraction sweep for as long as it
     * likes, and the sweep never finishes its batch.
     */
    @Test
    void aProcessThatNeverReturns_isKilled() throws Exception {
        var engine = engine(script("#!/bin/sh\nsleep 30\n"), 1);

        assertThatThrownBy(() -> engine.recognise(new byte[] {1}))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void missingBinary_throwsRatherThanReturningNothing() {
        var engine = engine(tempDir.resolve("does-not-exist").toString(), 10);

        assertThatThrownBy(() -> engine.recognise(new byte[] {1}))
                .isInstanceOf(OcrException.class);
    }

    private static TesseractProcessOcrEngine engine(String binary, int timeoutSeconds) {
        return new TesseractProcessOcrEngine(binary, timeoutSeconds);
    }

    private String script(String body) throws Exception {
        Path path = tempDir.resolve("fake-tesseract");
        Files.writeString(path, body, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path.toString();
    }
}
