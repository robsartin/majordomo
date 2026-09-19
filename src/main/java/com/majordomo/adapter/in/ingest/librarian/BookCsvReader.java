package com.majordomo.adapter.in.ingest.librarian;

import com.majordomo.domain.model.librarian.BookImportRow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the shelf CSV into {@link BookImportRow}s.
 *
 * <p>Inbound adapter: it drives the catalog use case rather than being called
 * by it, which is why it lives under {@code adapter.in} and the use case takes
 * already-parsed rows.
 *
 * <p>Expected header: {@code Title, Author, Photo, Notes, Rating}.
 */
public class BookCsvReader {

    private static final int COLUMNS = 5;

    /**
     * Reads every data row, failing on the first one that cannot be parsed.
     *
     * @param source the CSV source, header row included
     * @return the parsed rows in file order
     * @throws BookCsvFormatException if any row is malformed
     */
    public List<BookImportRow> read(Reader source) {
        var rows = new ArrayList<BookImportRow>();
        try (var buffered = new BufferedReader(source)) {
            String line = buffered.readLine();
            if (line == null) {
                return List.of();
            }
            int lineNumber = 1;
            while ((line = buffered.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                rows.add(parse(line, lineNumber));
            }
        } catch (IOException e) {
            throw new BookCsvFormatException("Could not read the shelf CSV: " + e.getMessage());
        }
        return List.copyOf(rows);
    }

    private BookImportRow parse(String line, int lineNumber) {
        List<String> fields = splitCsv(line);
        if (fields.size() != COLUMNS) {
            throw new BookCsvFormatException(
                    "line " + lineNumber + ": expected " + COLUMNS + " columns, found " + fields.size());
        }
        String title = fields.get(0).trim();
        if (title.isBlank()) {
            throw new BookCsvFormatException("line " + lineNumber + ": title is blank");
        }
        return new BookImportRow(
                title,
                fields.get(1).trim(),
                fields.get(2).trim(),
                fields.get(3).trim(),
                parseRating(fields.get(4).trim(), lineNumber));
    }

    private Integer parseRating(String raw, int lineNumber) {
        if (raw.isEmpty()) {
            return null;
        }
        int rating;
        try {
            rating = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new BookCsvFormatException("line " + lineNumber + ": rating '" + raw + "' is not a number");
        }
        if (rating < 1 || rating > 5) {
            throw new BookCsvFormatException("line " + lineNumber + ": rating " + rating + " is outside 1-5");
        }
        return rating;
    }

    private List<String> splitCsv(String line) {
        var fields = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
