package com.majordomo.adapter.in.web.ledger;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.domain.port.in.ledger.ExportSpendUseCase;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Downloads an organization's spend as a CSV — a per-property breakdown plus an
 * "All properties" rollup — for taxes/insurance. Sibling to the ledger dashboard
 * at {@code /ledger}. Row assembly lives in {@link ExportSpendUseCase}; this
 * controller only serialises and sets download headers.
 */
@Controller
public class LedgerExportController {

    private final ExportSpendUseCase exportSpend;
    private final SpendCsvWriter csvWriter;

    /**
     * Constructs the controller.
     *
     * @param exportSpend spend-export use case
     * @param csvWriter   CSV serialiser
     */
    public LedgerExportController(ExportSpendUseCase exportSpend, SpendCsvWriter csvWriter) {
        this.exportSpend = exportSpend;
        this.csvWriter = csvWriter;
    }

    /**
     * Returns the org's spend as a downloadable CSV.
     *
     * @param orgContext authenticated user + organization
     * @return a {@code text/csv} attachment
     */
    @GetMapping(value = "/ledger/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(OrgContext orgContext) {
        String body = csvWriter.toCsv(exportSpend.spendRows(orgContext.organizationId()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"majordomo-spend.csv\"")
                .body(body);
    }
}
