package com.majordomo.adapter.out.extraction;

import com.majordomo.domain.model.attachment.ExtractedText;
import com.majordomo.domain.port.out.TextExtractionPort;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads text out of PDFs and plain-text attachments (#298, ADR-0026).
 *
 * <p>PDFBox rather than Tika: uploads are limited to PDF, plain text and
 * images, so Tika's breadth over a hundred formats buys nothing here and brings
 * a large transitive tree with it. Images are reported unsupported rather than
 * guessed at — OCR is its own decision.
 */
@Component
public class PdfBoxTextExtractionAdapter implements TextExtractionPort {

    private static final Logger LOG = LoggerFactory.getLogger(PdfBoxTextExtractionAdapter.class);

    /**
     * Characters of extracted text kept per attachment.
     *
     * <p>Postgres refuses a tsvector over 1MB, and the search column is
     * generated from this text — so an oversized document would not merely
     * search poorly, it would make the row impossible to write. The cap leaves
     * room for the worst case, where every word is distinct.
     */
    public static final int MAX_TEXT_CHARS = 500_000;

    @Override
    public ExtractedText extract(String contentType, InputStream content) {
        if (contentType == null) {
            return ExtractedText.unsupported();
        }
        return switch (contentType) {
            case "application/pdf" -> fromPdf(content);
            case "text/plain" -> fromPlainText(content);
            default -> ExtractedText.unsupported();
        };
    }

    private ExtractedText fromPdf(InputStream content) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(content))) {
            return result(new PDFTextStripper().getText(document));
        } catch (Exception e) {
            // Deliberately broad: PDFBox reports malformed input as anything from
            // IOException to a runtime error out of a parser, and the caller needs
            // one answer — this file cannot be read — for all of them.
            LOG.warn("PDF text extraction failed: {}", e.toString());
            return ExtractedText.failed();
        }
    }

    private ExtractedText fromPlainText(InputStream content) {
        try {
            return result(new String(content.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.warn("Plain-text extraction failed: {}", e.toString());
            return ExtractedText.failed();
        }
    }

    private static ExtractedText result(String text) {
        if (text == null || text.isBlank()) {
            return ExtractedText.empty();
        }
        return ExtractedText.extracted(
                text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text);
    }
}
