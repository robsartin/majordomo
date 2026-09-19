package com.majordomo.adapter.out.extraction;

import com.majordomo.domain.model.attachment.ExtractedText;
import com.majordomo.domain.port.out.TextExtractionPort;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads text out of images and scanned PDFs with Tesseract (#341, ADR-0027).
 *
 * <p>A scanned PDF is images in a wrapper, so its pages are rendered before
 * they can be recognised. Rendering and OCR are the slowest work in this
 * codebase, which is why the page count is capped: a long scanned document
 * would otherwise hold the whole extraction batch behind it.
 */
@Component
public class TesseractOcrAdapter implements TextExtractionPort {

    private static final Logger LOG = LoggerFactory.getLogger(TesseractOcrAdapter.class);

    private final OcrEngine engine;
    private final int maxPages;
    private final int renderDpi;

    /**
     * Constructs the adapter.
     *
     * @param engine    the recognition engine
     * @param maxPages  pages of a PDF to attempt before giving up
     * @param renderDpi resolution PDF pages are rendered at before recognition
     */
    public TesseractOcrAdapter(
            OcrEngine engine,
            @Value("${majordomo.storage.ocr-max-pages:20}") int maxPages,
            @Value("${majordomo.storage.ocr-render-dpi:200}") int renderDpi) {
        this.engine = engine;
        this.maxPages = maxPages;
        this.renderDpi = renderDpi;
    }

    @Override
    public ExtractedText extract(String contentType, InputStream content) {
        if (contentType == null) {
            return ExtractedText.unsupported();
        }
        try {
            return switch (contentType) {
                case "image/jpeg", "image/png" -> result(engine.recognise(content.readAllBytes()));
                case "application/pdf" -> fromScannedPdf(content);
                default -> ExtractedText.unsupported();
            };
        } catch (Exception e) {
            LOG.warn("OCR failed for {}: {}", contentType, e.toString());
            return ExtractedText.failed();
        }
    }

    private ExtractedText fromScannedPdf(InputStream content) throws Exception {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(content))) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), maxPages);
            List<String> recognised = new ArrayList<>();
            for (int page = 0; page < pages; page++) {
                recognised.add(engine.recognise(png(renderer.renderImageWithDPI(page, renderDpi))));
            }
            if (document.getNumberOfPages() > pages) {
                LOG.info("OCR stopped at page {} of {}", pages, document.getNumberOfPages());
            }
            return result(String.join("\n", recognised));
        }
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static ExtractedText result(String text) {
        return text == null || text.isBlank() ? ExtractedText.empty() : ExtractedText.extracted(text);
    }
}
