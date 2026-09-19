package com.majordomo.adapter.out.extraction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the Tesseract binary on one image (#341, ADR-0027).
 *
 * <p>The image goes to a temporary file rather than the process's stdin. Piping
 * in while reading out deadlocks the moment either buffer fills, and a page of
 * recognised text is easily large enough for that to happen — rarely, and only
 * on the bigger documents.
 */
@Component
public class TesseractProcessOcrEngine implements OcrEngine {

    private final String binary;
    private final int timeoutSeconds;

    /**
     * Constructs the engine.
     *
     * @param binary         the tesseract executable, on PATH or absolute
     * @param timeoutSeconds how long one image may take before it is killed
     */
    public TesseractProcessOcrEngine(
            @Value("${majordomo.storage.ocr-binary:tesseract}") String binary,
            @Value("${majordomo.storage.ocr-timeout-seconds:60}") int timeoutSeconds) {
        this.binary = binary;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String recognise(byte[] image) {
        Path input = null;
        Path errors = null;
        Process process = null;
        try {
            input = Files.createTempFile("majordomo-ocr-", ".img");
            errors = Files.createTempFile("majordomo-ocr-", ".err");
            Files.write(input, image);

            process = new ProcessBuilder(binary, input.toString(), "stdout")
                    .redirectError(errors.toFile())
                    .start();

            // Drained on its own thread. Reading the process's output to the end
            // on this one blocks until the process exits, so the timeout below
            // could never fire — it would sit there for as long as the engine
            // took, which is exactly what the timeout exists to prevent.
            AtomicReference<String> output = new AtomicReference<>("");
            Process running = process;
            Thread reader = Thread.ofVirtual().start(() -> {
                try {
                    output.set(new String(running.getInputStream().readAllBytes(),
                            StandardCharsets.UTF_8));
                } catch (IOException e) {
                    output.set("");
                }
            });

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                reader.join();
                throw new OcrException("OCR timed out after " + timeoutSeconds + "s");
            }
            reader.join();

            if (process.exitValue() != 0) {
                throw new OcrException("OCR exited " + process.exitValue() + ": "
                        + Files.readString(errors).strip());
            }
            return output.get();
        } catch (OcrException e) {
            throw e;
        } catch (IOException e) {
            throw new OcrException("Could not run OCR with '" + binary + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException("Interrupted while running OCR", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            delete(input);
            delete(errors);
        }
    }

    private static void delete(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // A leftover file in the temp directory is not worth failing an
                // extraction over, and the OS clears them anyway.
            }
        }
    }
}
