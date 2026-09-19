package com.majordomo.adapter.in.ingest.librarian;

/**
 * Thrown when a row of the shelf CSV cannot be read.
 *
 * <p>Reading the shelf must fail loudly rather than skipping what it cannot
 * parse. A lenient importer that drops bad rows turns "I could not read this"
 * into "this book does not exist", and the owner has no way to tell a shelf of
 * 57 from a shelf of 57 minus the three rows that silently failed.
 */
public class BookCsvFormatException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what was wrong, naming the offending line
     */
    public BookCsvFormatException(String message) {
        super(message);
    }
}
