package com.majordomo.adapter.out.extraction;

import com.majordomo.domain.model.attachment.ExtractedText;
import com.majordomo.domain.model.attachment.ExtractionStatus;
import com.majordomo.domain.port.out.TextExtractionPort;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the chain that falls back to OCR (#341).
 *
 * <p>The interesting behaviour is which results are worth a second attempt.
 * EMPTY means a readable document with no text layer — a scan. UNSUPPORTED
 * means a type the first extractor does not read — an image. Both are exactly
 * what OCR is for. EXTRACTED and FAILED are not: one already succeeded, and the
 * other means the bytes could not be read at all, which OCR will not fix.
 */
class FallbackTextExtractionAdapterTest {

    @Test
    void extractedResult_isReturnedWithoutRunningOcr() {
        RecordingExtractor ocr = new RecordingExtractor(ExtractedText.extracted("from ocr"));
        var chain = new FallbackTextExtractionAdapter(
                fixed(ExtractedText.extracted("from pdfbox")), ocr);

        ExtractedText result = chain.extract("application/pdf", bytes("%PDF"));

        assertThat(result.text()).isEqualTo("from pdfbox");
        assertThat(ocr.calls).isEmpty();
    }

    @Test
    void failedResult_isNotRetriedByOcr() {
        RecordingExtractor ocr = new RecordingExtractor(ExtractedText.extracted("from ocr"));
        var chain = new FallbackTextExtractionAdapter(fixed(ExtractedText.failed()), ocr);

        assertThat(chain.extract("application/pdf", bytes("not a pdf")).status())
                .isEqualTo(ExtractionStatus.FAILED);
        assertThat(ocr.calls).isEmpty();
    }

    @Test
    void emptyResult_fallsBackToOcr() {
        RecordingExtractor ocr = new RecordingExtractor(ExtractedText.extracted("Carrier 58STA"));
        var chain = new FallbackTextExtractionAdapter(fixed(ExtractedText.empty()), ocr);

        ExtractedText result = chain.extract("application/pdf", bytes("scanned pdf"));

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(result.text()).isEqualTo("Carrier 58STA");
    }

    @Test
    void unsupportedResult_fallsBackToOcr() {
        RecordingExtractor ocr = new RecordingExtractor(ExtractedText.extracted("TOTAL 42.10"));
        var chain = new FallbackTextExtractionAdapter(fixed(ExtractedText.unsupported()), ocr);

        assertThat(chain.extract("image/jpeg", bytes("jpeg")).text()).isEqualTo("TOTAL 42.10");
    }

    /**
     * OCR finding nothing must not overwrite the first extractor's verdict. A
     * photograph of a wall really is EMPTY; reporting it FAILED because the OCR
     * step came back blank would claim the file is unreadable when it is not.
     */
    @Test
    void whenOcrFindsNothing_theOriginalVerdictStands() {
        var chain = new FallbackTextExtractionAdapter(
                fixed(ExtractedText.unsupported()),
                new RecordingExtractor(ExtractedText.empty()));

        assertThat(chain.extract("image/png", bytes("png")).status())
                .isEqualTo(ExtractionStatus.UNSUPPORTED);
    }

    /**
     * Both extractors read the same stream, and a stream can only be read once.
     * Without buffering, OCR would receive nothing and every scanned PDF would
     * come back empty — silently, since an empty read is indistinguishable from
     * a page with no text.
     */
    @Test
    void bothExtractorsSeeTheSameBytes() {
        RecordingExtractor first = new RecordingExtractor(ExtractedText.empty());
        RecordingExtractor ocr = new RecordingExtractor(ExtractedText.extracted("ok"));
        var chain = new FallbackTextExtractionAdapter(first, ocr);

        chain.extract("application/pdf", bytes("the original bytes"));

        assertThat(first.calls).containsExactly("the original bytes");
        assertThat(ocr.calls).containsExactly("the original bytes");
    }

    private static InputStream bytes(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static TextExtractionPort fixed(ExtractedText result) {
        return new RecordingExtractor(result);
    }

    /** Returns a fixed result and remembers the bytes it was handed. */
    private static final class RecordingExtractor implements TextExtractionPort {
        private final ExtractedText result;
        private final List<String> calls = new ArrayList<>();

        RecordingExtractor(ExtractedText result) {
            this.result = result;
        }

        @Override
        public ExtractedText extract(String contentType, InputStream content) {
            try {
                calls.add(new String(content.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return result;
        }
    }
}
