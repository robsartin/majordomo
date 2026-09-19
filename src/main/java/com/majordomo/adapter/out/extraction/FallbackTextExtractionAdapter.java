package com.majordomo.adapter.out.extraction;

import com.majordomo.domain.model.attachment.ExtractedText;
import com.majordomo.domain.model.attachment.ExtractionStatus;
import com.majordomo.domain.port.out.TextExtractionPort;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Tries a document's own text first and falls back to OCR (#341, ADR-0027).
 *
 * <p>Only two outcomes are worth a second attempt. {@code EMPTY} is a readable
 * document with no text layer — a scan. {@code UNSUPPORTED} is a type the first
 * extractor does not read — an image. Those are exactly what OCR is for.
 * {@code EXTRACTED} already succeeded, and {@code FAILED} means the bytes could
 * not be read at all, which OCR will not repair.
 */
@Component
@Primary
public class FallbackTextExtractionAdapter implements TextExtractionPort {

    private final TextExtractionPort primary;
    private final TextExtractionPort ocr;

    /**
     * Constructs the chain.
     *
     * @param primary the document's own text, normally PDFBox
     * @param ocr     the fallback used when there is no text to read
     */
    @Autowired
    public FallbackTextExtractionAdapter(
            PdfBoxTextExtractionAdapter primary, TesseractOcrAdapter ocr) {
        this.primary = primary;
        this.ocr = ocr;
    }

    /**
     * Constructs the chain from arbitrary extractors, for testing.
     *
     * <p>The constructor above carries {@code @Autowired} because of this one:
     * with two candidates and no annotation, Spring looks for a no-arg
     * constructor, finds none, and the context fails to start.
     *
     * @param primary the first extractor
     * @param ocr     the fallback
     */
    FallbackTextExtractionAdapter(TextExtractionPort primary, TextExtractionPort ocr) {
        this.primary = primary;
        this.ocr = ocr;
    }

    @Override
    public ExtractedText extract(String contentType, InputStream content) {
        byte[] bytes = readAll(content);
        if (bytes == null) {
            return ExtractedText.failed();
        }

        ExtractedText first = primary.extract(contentType, new ByteArrayInputStream(bytes));
        if (!worthOcr(first.status())) {
            return first;
        }

        ExtractedText second = ocr.extract(contentType, new ByteArrayInputStream(bytes));
        // OCR finding nothing does not overturn the first verdict. A photograph
        // of a wall really is empty, and calling it unreadable would be a
        // stronger claim than anything actually observed.
        return second.status() == ExtractionStatus.EXTRACTED ? second : first;
    }

    private static boolean worthOcr(ExtractionStatus status) {
        return status == ExtractionStatus.EMPTY || status == ExtractionStatus.UNSUPPORTED;
    }

    /**
     * Buffers the content so both extractors can read it. A stream is read once;
     * without this the OCR step would receive nothing and every scanned document
     * would come back empty, indistinguishably from a page that genuinely has no
     * text. Uploads are capped at 10MB, so holding one in memory is bounded.
     */
    private static byte[] readAll(InputStream content) {
        try {
            return content.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }
}
