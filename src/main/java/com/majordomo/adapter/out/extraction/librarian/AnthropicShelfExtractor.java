package com.majordomo.adapter.out.extraction.librarian;

import com.majordomo.adapter.out.llm.AnthropicVisionClient;
import com.majordomo.domain.model.librarian.BookImportRow;
import com.majordomo.domain.port.out.librarian.ShelfPhotoExtractionPort;

import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

/**
 * Reads a shelf photograph into import rows using a vision model.
 *
 * <p>The prompt asks for a doubt note per book rather than a confidence score.
 * A note is something a reviewer can act on — "spine partly obscured" tells
 * them what to check — where a number invites the catalog to treat a guess as
 * measured. The grading itself happens in the domain, from the note.
 */
@Component
public class AnthropicShelfExtractor implements ShelfPhotoExtractionPort {

    private static final String SYSTEM_PROMPT = """
            You transcribe photographs of bookshelves into structured data.

            Return ONLY a JSON object of the form:
            {"books": [{"title": "...", "author": "...", "note": "..."}]}

            Rules:
            - One entry per distinct book you can see, in shelf order.
            - Transcribe what is printed on the spine. Do not correct, expand or
              tidy a title, and do not supply an author that is not visible.
            - If a spine is hard to read, still include the book, and say why in
              "note" — for example "spine partly obscured", "title partly cut
              off", "author not visible".
            - If you are unsure of a title or author and are filling it in from
              your own knowledge rather than reading it, say so in "note" using
              the words "filled from knowledge" or "inferred".
            - Use an empty string for "note" only when the spine read cleanly.
            - Never guess or invent a rating. Do not include a rating field.
            - If no books are legible, return {"books": []}.
            """;

    private static final String USER_PROMPT =
            "Transcribe every book visible on this shelf.";

    private final AnthropicVisionClient vision;
    private final ShelfExtractionParser parser;

    /**
     * Constructs the extractor.
     *
     * @param vision the image-capable Messages API client
     * @param parser the response parser
     */
    public AnthropicShelfExtractor(AnthropicVisionClient vision, ShelfExtractionParser parser) {
        this.vision = vision;
        this.parser = parser;
    }

    @Override
    public List<BookImportRow> extract(byte[] image, String mediaType, String sourcePhoto) {
        if (image == null || image.length == 0) {
            throw new ShelfExtractionException("No image data to extract from", null);
        }
        String base64 = Base64.getEncoder().encodeToString(image);
        String answer;
        try {
            answer = vision.describe(SYSTEM_PROMPT, USER_PROMPT, base64, mediaType);
        } catch (RuntimeException e) {
            // A model or transport failure must not be reported as "this shelf
            // holds no books" — the owner could not tell the two apart.
            throw new ShelfExtractionException("Shelf photo extraction failed", e);
        }
        return parser.parse(answer, sourcePhoto);
    }
}
