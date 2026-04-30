package com.majordomo.adapter.in.web.ledger;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.domain.model.ledger.SpendSummary;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Serves the Ledger spend-dashboard web page. Sibling to the REST
 * {@code LedgerController} at {@code /api/ledger}, which is untouched.
 */
@Controller
@RequestMapping("/ledger")
public class LedgerPageController {

    private final QuerySpendUseCase spendUseCase;
    private final PropertyRepository propertyRepository;

    /**
     * One row in the per-property spend table.
     *
     * @param property the property
     * @param summary  the property's spend summary
     */
    public record PropertySpendRow(Property property, SpendSummary summary) { }

    /**
     * Constructs the ledger page controller.
     *
     * @param spendUseCase       inbound port for spend queries
     * @param propertyRepository outbound port for property reads
     */
    public LedgerPageController(QuerySpendUseCase spendUseCase,
                                PropertyRepository propertyRepository) {
        this.spendUseCase = spendUseCase;
        this.propertyRepository = propertyRepository;
    }

    /**
     * Renders the spend dashboard for the user's organization.
     *
     * @param orgContext authenticated user + organization
     * @param model      Thymeleaf model
     * @return the {@code ledger} template
     */
    @GetMapping
    public String dashboard(OrgContext orgContext, Model model) {
        UUID orgId = orgContext.organizationId();

        SpendSummary orgSpend = spendUseCase.spendForOrganization(orgId);
        BigDecimal projected = spendUseCase.projectedAnnualSpend(orgId);

        List<PropertySpendRow> rows = new ArrayList<>();
        for (Property p : propertyRepository.findByOrganizationId(orgId)) {
            if (p.getArchivedAt() != null) {
                continue;
            }
            rows.add(new PropertySpendRow(p, spendUseCase.spendForProperty(p.getId())));
        }
        rows.sort(Comparator.comparing(
                (PropertySpendRow r) -> nullToZero(r.summary().totalCost()),
                Comparator.reverseOrder()));

        model.addAttribute("orgSpend", orgSpend);
        model.addAttribute("projectedAnnualSpend", projected);
        model.addAttribute("rows", rows);
        model.addAttribute("username", orgContext.username());
        return "ledger";
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
