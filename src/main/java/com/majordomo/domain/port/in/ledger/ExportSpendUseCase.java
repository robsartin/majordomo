package com.majordomo.domain.port.in.ledger;

import com.majordomo.domain.model.ledger.SpendExportRow;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port for producing a tabular spend export for an organization —
 * one row per property plus an organization rollup row — for download
 * (e.g. CSV, for taxes/insurance).
 */
public interface ExportSpendUseCase {

    /**
     * Builds the spend export rows for an organization: a row per non-archived
     * property (highest total first), followed by an "All properties" rollup row.
     *
     * @param organizationId the organization
     * @return the export rows, rollup last
     */
    List<SpendExportRow> spendRows(UUID organizationId);
}
