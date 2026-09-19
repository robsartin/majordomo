package com.majordomo.adapter.out.extraction;

import com.majordomo.domain.model.attachment.ExtractedText;
import com.majordomo.domain.model.attachment.ExtractionStatus;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PDF and plain-text extraction (#298).
 *
 * <p>Fixtures are built here rather than checked in, so what the extractor is
 * asked to read is visible in the test that asserts on it.
 */
class PdfBoxTextExtractionAdapterTest {

    private final PdfBoxTextExtractionAdapter adapter = new PdfBoxTextExtractionAdapter();

    @Test
    void pdf_yieldsItsText() {
        ExtractedText result = adapter.extract("application/pdf",
                new ByteArrayInputStream(pdfContaining("Carrier furnace model 58STA")));

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(result.text()).contains("Carrier furnace model 58STA");
    }

    /**
     * A scanned manual is a PDF with no text layer. It has to be distinguishable
     * from one not yet processed, or the sweep retries it forever — and the set
     * of EMPTY attachments is exactly the set that would benefit from OCR later.
     */
    @Test
    void pdfWithNoTextLayer_reportsEmptyRatherThanFailing() {
        ExtractedText result = adapter.extract("application/pdf",
                new ByteArrayInputStream(pdfContaining(null)));

        assertThat(result.status()).isEqualTo(ExtractionStatus.EMPTY);
        assertThat(result.text()).isNull();
    }

    @Test
    void plainText_isReadDirectly() {
        ExtractedText result = adapter.extract("text/plain",
                new ByteArrayInputStream("boiler serviced 2026-03-04".getBytes(StandardCharsets.UTF_8)));

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(result.text()).contains("boiler serviced");
    }

    @Test
    void image_reportsUnsupported() {
        ExtractedText result = adapter.extract("image/jpeg",
                new ByteArrayInputStream(new byte[] {1, 2, 3}));

        assertThat(result.status()).isEqualTo(ExtractionStatus.UNSUPPORTED);
    }

    /**
     * A corrupt file must not escape as an exception. The sweep processes a
     * batch, and one unreadable attachment taking the batch down would stall
     * every attachment queued behind it.
     */
    @Test
    void bytesThatAreNotAPdf_reportFailedWithoutThrowing() {
        ExtractedText result = adapter.extract("application/pdf",
                new ByteArrayInputStream("this is not a PDF".getBytes(StandardCharsets.UTF_8)));

        assertThat(result.status()).isEqualTo(ExtractionStatus.FAILED);
    }

    /**
     * Postgres refuses a tsvector over 1MB, and the search column is generated
     * from this text — so an oversized document would not degrade search, it
     * would make the row impossible to insert. A 10MB PDF is within the allowed
     * upload size, so this is reachable with an ordinary upload rather than a
     * hypothetical one.
     */
    @Test
    void veryLongText_isCappedSoTheGeneratedSearchVectorStaysLegal() {
        String huge = "furnace ".repeat(300_000);
        assertThat(huge.length()).isGreaterThan(PdfBoxTextExtractionAdapter.MAX_TEXT_CHARS);

        ExtractedText result = adapter.extract("text/plain",
                new ByteArrayInputStream(huge.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(result.text()).hasSize(PdfBoxTextExtractionAdapter.MAX_TEXT_CHARS);
    }

    /** Builds a one-page PDF, with the given text or with no text layer at all. */
    private static byte[] pdfContaining(String text) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (text != null) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("could not build the PDF fixture", e);
        }
    }
}
