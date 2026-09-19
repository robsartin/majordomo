package com.majordomo.domain.port.out.librarian;

import com.majordomo.domain.model.librarian.BookImportRow;

import java.util.List;

/**
 * Outbound port for reading a shelf photograph into import rows.
 *
 * <p>Implementations must mark every row they produce as
 * {@code PHOTO_EXTRACTION}, which is what keeps unreviewed rows out of the
 * catalog's trusted tier (ADR-0023), and must never invent a rating — that is
 * the owner's judgement.
 *
 * <p>A photograph with no legible books is an empty list. Only a failure to
 * read the photograph at all is an exception: reporting a failed extraction as
 * an empty shelf would be indistinguishable from success.
 */
public interface ShelfPhotoExtractionPort {

    /**
     * Extracts books visible in one shelf photograph.
     *
     * @param image       the raw image bytes
     * @param mediaType   the image's media type, e.g. {@code image/jpeg}
     * @param sourcePhoto an identifier stamped on each row for provenance
     * @return the extracted rows, empty when nothing legible was found
     */
    List<BookImportRow> extract(byte[] image, String mediaType, String sourcePhoto);
}
