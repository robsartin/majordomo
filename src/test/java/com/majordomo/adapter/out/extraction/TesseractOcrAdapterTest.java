package com.majordomo.adapter.out.extraction;

import com.majordomo.domain.model.attachment.ExtractedText;
import com.majordomo.domain.model.attachment.ExtractionStatus;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OCR of images and scanned PDFs (#341).
 *
 * <p>The engine itself is stubbed here; that it drives the real Tesseract
 * binary correctly is {@code TesseractOcrIntegrationTest}'s job. What is under
 * test is everything around it — which types are sent, how pages are handled,
 * and what happens when the engine finds nothing or falls over.
 */
class TesseractOcrAdapterTest {

    @Test
    void image_yieldsTheEnginesText() {
        StubEngine engine = StubEngine.returning("TOTAL 42.10");
        ExtractedText result = adapter(engine, 20).extract("image/jpeg", bytes(new byte[] {1, 2}));

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(result.text()).isEqualTo("TOTAL 42.10");
        assertThat(engine.calls).hasSize(1);
    }

    /**
     * A photograph of a wall is genuinely empty. Reporting it FAILED would claim
     * the file could not be read, which is a stronger and different statement.
     */
    @Test
    void imageWithNoLegibleText_reportsEmpty() {
        ExtractedText result = adapter(StubEngine.returning("  \n "), 20)
                .extract("image/png", bytes(new byte[] {1}));

        assertThat(result.status()).isEqualTo(ExtractionStatus.EMPTY);
    }

    @Test
    void engineFailure_reportsFailedWithoutThrowing() {
        ExtractedText result = adapter(StubEngine.throwing(), 20)
                .extract("image/png", bytes(new byte[] {1}));

        assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
    }

    /** A scanned PDF is images in a wrapper, so every page has to be rendered. */
    @Test
    void scannedPdf_ocrsEveryPage() {
        StubEngine engine = StubEngine.returningPerCall("first page", "second page");

        ExtractedText result = adapter(engine, 20).extract("application/pdf", bytes(pdfOf(2)));

        assertThat(engine.calls).hasSize(2);
        assertThat(result.text()).contains("first page").contains("second page");
    }

    /**
     * Rendering and OCR are the slowest things this codebase does, and a long
     * scanned document would otherwise hold the sweep's batch for minutes.
     */
    @Test
    void longPdf_stopsAtThePageCap() {
        StubEngine engine = StubEngine.returning("page");

        adapter(engine, 2).extract("application/pdf", bytes(pdfOf(5)));

        assertThat(engine.calls).hasSize(2);
    }

    @Test
    void unreadableTypes_neverReachTheEngine() {
        StubEngine engine = StubEngine.returning("should not happen");

        assertThat(adapter(engine, 20).extract("text/plain", bytes(new byte[] {1})).status())
                .isEqualTo(ExtractionStatus.UNSUPPORTED);
        assertThat(engine.calls).isEmpty();
    }

    private static TesseractOcrAdapter adapter(OcrEngine engine, int maxPages) {
        return new TesseractOcrAdapter(engine, maxPages, 200);
    }

    private static ByteArrayInputStream bytes(byte[] content) {
        return new ByteArrayInputStream(content);
    }

    /** A PDF of the given page count, with no text layer — i.e. a scan. */
    private static byte[] pdfOf(int pages) {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("could not build the PDF fixture", e);
        }
    }

    /** Records every call and answers from a fixed script. */
    private static final class StubEngine implements OcrEngine {
        private final List<String> answers;
        private final boolean fail;
        private final List<byte[]> calls = new ArrayList<>();

        private StubEngine(List<String> answers, boolean fail) {
            this.answers = answers;
            this.fail = fail;
        }

        static StubEngine returning(String answer) {
            return new StubEngine(List.of(answer), false);
        }

        static StubEngine returningPerCall(String... answers) {
            return new StubEngine(List.of(answers), false);
        }

        static StubEngine throwing() {
            return new StubEngine(List.of(), true);
        }

        @Override
        public String recognise(byte[] image) {
            calls.add(image);
            if (fail) {
                throw new IllegalStateException("tesseract exited 1");
            }
            return answers.get(Math.min(calls.size() - 1, answers.size() - 1));
        }
    }
}
