package com.majordomo.adapter.out.extraction.librarian;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.domain.model.librarian.BookImportRow;
import com.majordomo.domain.model.librarian.ImportSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the model's answer into import rows.
 *
 * <p>Separated from the adapter that calls the model so the parsing — the part
 * that actually fails in interesting ways — is testable without a network call
 * or a stubbed SDK.
 */
@org.springframework.stereotype.Component
public class ShelfExtractionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parses one extraction response.
     *
     * @param modelOutput the model's raw answer, optionally fenced in markdown
     * @param sourcePhoto an identifier for the photograph, stamped on each row
     * @return the extracted rows, empty when the photograph held no books
     * @throws ShelfExtractionException if the answer cannot be read
     */
    public List<BookImportRow> parse(String modelOutput, String sourcePhoto) {
        Extraction extraction;
        try {
            extraction = MAPPER.readValue(unfence(modelOutput), Extraction.class);
        } catch (JsonProcessingException e) {
            throw new ShelfExtractionException("Could not read the extraction response", e);
        }
        if (extraction == null || extraction.books() == null) {
            // An answer without a books array is a failed read, not an empty
            // shelf. Reporting it as zero books would look like a successful
            // extraction of a blank photograph.
            throw new ShelfExtractionException(
                    "Extraction response contained no 'books' array", null);
        }
        var rows = new ArrayList<BookImportRow>(extraction.books().size());
        for (ExtractedBook book : extraction.books()) {
            if (book == null || book.title() == null || book.title().isBlank()) {
                continue;
            }
            rows.add(new BookImportRow(
                    book.title().trim(),
                    book.author() == null ? "" : book.author().trim(),
                    sourcePhoto,
                    book.note() == null ? "" : book.note().trim(),
                    // Never a rating: that is the owner's judgement, and a model
                    // has no basis for one. Any rating the model volunteers is
                    // dropped here rather than reaching the catalog.
                    null,
                    ImportSource.PHOTO_EXTRACTION));
        }
        return List.copyOf(rows);
    }

    /** Models often wrap JSON in a markdown fence despite being asked not to. */
    private String unfence(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline < 0 || lastFence <= firstNewline) {
            return trimmed;
        }
        return trimmed.substring(firstNewline + 1, lastFence).trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Extraction(List<ExtractedBook> books) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExtractedBook(String title, String author, String note) { }
}
