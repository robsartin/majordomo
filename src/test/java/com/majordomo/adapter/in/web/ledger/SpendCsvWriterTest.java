package com.majordomo.adapter.in.web.ledger;

import com.majordomo.domain.model.ledger.SpendExportRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpendCsvWriterTest {

    private final SpendCsvWriter writer = new SpendCsvWriter();

    @Test
    void writesHeaderThenRowsWithCrlf() {
        String csv = writer.toCsv(List.of(
                new SpendExportRow("Furnace", new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("150"))));

        assertThat(csv).startsWith("Property,Purchase price,Maintenance cost,Total cost\r\n");
        assertThat(csv).contains("Furnace,100,50,150\r\n");
    }

    @Test
    void quotesFieldsContainingCommasOrQuotes() {
        String csv = writer.toCsv(List.of(
                new SpendExportRow("Smith, Jr. \"cabin\"", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));

        // Comma-bearing name is quoted; embedded quotes are doubled.
        assertThat(csv).contains("\"Smith, Jr. \"\"cabin\"\"\",0,0,0\r\n");
    }

    @Test
    void rendersNullAmountsAsZero() {
        String csv = writer.toCsv(List.of(
                new SpendExportRow("Roof", null, null, null)));

        assertThat(csv).contains("Roof,0,0,0\r\n");
    }
}
