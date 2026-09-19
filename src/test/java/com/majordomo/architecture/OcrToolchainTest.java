package com.majordomo.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OCR engine has to be in the image the application actually runs in
 * (#341).
 *
 * <p>Without the binary nothing breaks loudly: every image and every scanned
 * document is recorded FAILED, which is a terminal status, so the attachments
 * are quietly written off one by one and never retried. The symptom is search
 * results that are missing rather than an error anyone sees.
 *
 * <p>Only the runtime stage counts. A package installed in the build stage is
 * discarded when the final image is assembled.
 */
class OcrToolchainTest {

    private static final Path DOCKERFILE = Path.of("Dockerfile");

    @Test
    void runtimeImage_installsTheOcrEngineAndItsLanguageData() throws IOException {
        String runtimeStage = runtimeStage();

        assertThat(runtimeStage)
                .as("tesseract binary in the runtime stage of %s", DOCKERFILE)
                .contains("tesseract-ocr");
        // Named explicitly although Debian's tesseract-ocr Depends on it today.
        // Without language data tesseract recognises nothing and still exits
        // zero, so this is the kind of requirement worth stating ourselves
        // rather than inheriting from another package's dependency list.
        assertThat(runtimeStage)
                .as("English language data")
                .contains("tesseract-ocr-eng");
    }

    /** Everything from the last {@code FROM} on: the stage that ships. */
    private static String runtimeStage() throws IOException {
        String dockerfile = Files.readString(DOCKERFILE);
        int lastFrom = dockerfile.lastIndexOf("\nFROM ");
        assertThat(lastFrom).as("a FROM line in %s", DOCKERFILE).isNotNegative();
        return dockerfile.substring(lastFrom);
    }
}
