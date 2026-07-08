package com.majordomo.adapter.in.web.ledger;

import com.majordomo.domain.model.ledger.SpendExportRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Serialises spend-export rows to CSV (RFC 4180): a header row, CRLF line
 * endings, and fields quoted/escaped when they contain a comma, quote, or
 * newline. Null amounts render as {@code 0}.
 */
@Component
public class SpendCsvWriter {

    private static final String CRLF = "\r\n";
    private static final String HEADER = "Property,Purchase price,Maintenance cost,Total cost";

    /**
     * Renders the rows as a CSV document.
     *
     * @param rows the export rows
     * @return the CSV document
     */
    public String toCsv(List<SpendExportRow> rows) {
        StringBuilder sb = new StringBuilder(HEADER).append(CRLF);
        for (SpendExportRow r : rows) {
            sb.append(escape(r.property())).append(',')
                    .append(amount(r.purchasePrice())).append(',')
                    .append(amount(r.maintenanceCost())).append(',')
                    .append(amount(r.totalCost())).append(CRLF);
        }
        return sb.toString();
    }

    private static String amount(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).toPlainString();
    }

    private static String escape(String field) {
        String value = field == null ? "" : field;
        if (value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
