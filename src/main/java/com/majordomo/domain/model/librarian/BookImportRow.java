package com.majordomo.domain.model.librarian;

/**
 * One row of the shelf CSV, exactly as transcribed and before any enrichment.
 *
 * <p>Deliberately dumb: the columns are carried verbatim, including the free-text
 * {@code notes} that records how legible the spine was. Deriving a
 * {@link Confidence} from those notes, and normalising title and author for
 * dedupe, are the importer's job rather than this type's.
 *
 * @param title  the title as transcribed
 * @param author the author string as transcribed, possibly listing several
 * @param photo  which shelf photograph the row came from
 * @param notes  transcription caveats, empty when the spine read cleanly
 * @param rating the owner's 1-5 rating, or {@code null} when the column is blank
 */
public record BookImportRow(
    String title,
    String author,
    String photo,
    String notes,
    Integer rating
) { }
