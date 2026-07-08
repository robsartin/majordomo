package com.majordomo.application.ledger;

import com.majordomo.domain.model.ledger.SpendExportRow;
import com.majordomo.domain.model.ledger.SpendSummary;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.ledger.ExportSpendUseCase;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Assembles spend-export rows for an organization: one row per non-archived
 * property (highest total first, mirroring the ledger dashboard order) plus a
 * final "All properties" rollup. Keeps this assembly out of the web layer so the
 * export endpoint carries no domain logic.
 */
@Service
public class SpendExportService implements ExportSpendUseCase {

    private static final String ROLLUP_LABEL = "All properties";

    private final QuerySpendUseCase spend;
    private final PropertyRepository properties;

    /**
     * Constructs the service.
     *
     * @param spend      spend query use case
     * @param properties property repository
     */
    public SpendExportService(QuerySpendUseCase spend, PropertyRepository properties) {
        this.spend = spend;
        this.properties = properties;
    }

    @Override
    public List<SpendExportRow> spendRows(UUID organizationId) {
        List<SpendExportRow> rows = new ArrayList<>();
        for (Property p : properties.findByOrganizationId(organizationId)) {
            if (p.getArchivedAt() != null) {
                continue;
            }
            SpendSummary s = spend.spendForProperty(p.getId());
            rows.add(new SpendExportRow(p.getName(),
                    s.purchasePrice(), s.maintenanceCost(), s.totalCost()));
        }
        rows.sort(Comparator.comparing(
                (SpendExportRow r) -> nullToZero(r.totalCost()), Comparator.reverseOrder()));

        SpendSummary org = spend.spendForOrganization(organizationId);
        rows.add(new SpendExportRow(ROLLUP_LABEL,
                org.purchasePrice(), org.maintenanceCost(), org.totalCost()));
        return rows;
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
