package com.majordomo.domain.port.out;

import com.majordomo.domain.model.attachment.ExtractedText;

import java.io.InputStream;

/**
 * Outbound port for reading an attachment's text so it can be searched (#298).
 *
 * <p>Implementations never throw for a document they cannot read. An unreadable
 * attachment is an outcome to record, not an error to propagate — the caller
 * processes a batch, and one bad file must not take the rest down with it.
 */
public interface TextExtractionPort {

    /**
     * Reads the text of a document.
     *
     * @param contentType the attachment's MIME type
     * @param content     the file content
     * @return what happened, and the text if there was any
     */
    ExtractedText extract(String contentType, InputStream content);
}
