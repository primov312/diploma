package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.analysis.FeatureBundle;
import com.rocketcredit.backend.analysis.AffordabilityRequest;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DbFinanceFeatureProvider implements FeatureProviders.FinanceFeatureProvider {
    private final JdbcTemplate jdbc;
    private final com.rocketcredit.backend.address.MonthlyCostResolver costs;

    public DbFinanceFeatureProvider(JdbcTemplate jdbc, com.rocketcredit.backend.address.MonthlyCostResolver costs) {
        this.jdbc = jdbc;
        this.costs = costs;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public FeatureBundle.Finance finance(FeatureProviders.Context ctx) {
        return financeBundle(ctx).finance();
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public FeatureProviders.FinanceFeatures financeBundle(FeatureProviders.Context ctx) {
        var rows = jdbc.query("""
                SELECT r.*,s.generation FROM financial_input_state s
                JOIN financial_input_revisions r ON r.user_id=s.user_id AND r.revision=s.current_revision
                WHERE s.user_id=?
                """, (rs, row) -> new FinanceRow(rs.getBigDecimal("monthly_net_income"),
                rs.getString("expense_mode"), rs.getString("housing_situation"), rs.getBigDecimal("housing_cost"),
                rs.getBigDecimal("groceries_cost"), rs.getBigDecimal("utilities_cost"),
                rs.getBigDecimal("transport_cost"), rs.getBigDecimal("other_living_costs"),
                rs.getBigDecimal("legacy_living_expenses"), rs.getBigDecimal("monthly_obligations"),
                rs.getInt("revision"), rs.getLong("generation")), ctx.userId());
        if (rows.isEmpty()) return new FeatureProviders.FinanceFeatures(null, null);
        FinanceRow row = rows.getFirst();
        var reference = costs.references(costs.currentContext(ctx.userId(), row.generation()));
        BigDecimal income = row.income() == null ? BigDecimal.ZERO : row.income();
        BigDecimal obligations = row.obligations();
        boolean itemized = "ITEMIZED".equals(row.mode());
        BigDecimal expenses;
        if ("AUTOMATIC".equals(row.mode())) {
            expenses = row.legacyLivingExpenses();
            if(reference.eligible()) {
                BigDecimal calculated=("RENTING".equals(row.housing()) ? reference.rent() : BigDecimal.ZERO)
                        .add(reference.groceries()).add(reference.other());
                expenses=expenses==null?calculated:expenses.max(calculated);
            }
        } else if (itemized) {
            BigDecimal rent = row.housingCost();
            BigDecimal groceries = row.groceriesCost();
            BigDecimal other = row.otherLivingCosts();
            if (reference.eligible()) {
                if ("RENTING".equals(row.housing()) && reference.rent() != null) rent = rent.max(reference.rent());
                if (reference.groceries() != null) groceries = groceries.max(reference.groceries());
                if (reference.other() != null) other = other.max(reference.other());
            }
            expenses = rent.add(groceries).add(row.utilitiesCost()).add(row.transportCost()).add(other);
        } else {
            expenses = row.legacyLivingExpenses();
            if (reference.eligible()) {
                BigDecimal floor = "RENTING".equals(row.housing()) && reference.rent() != null
                        ? reference.rent() : BigDecimal.ZERO;
                if (reference.groceries() != null) floor = floor.add(reference.groceries());
                if (reference.other() != null) floor = floor.add(reference.other());
                expenses = expenses.max(floor);
            }
        }
        FeatureBundle.Finance legacyFinance = row.income() != null && expenses != null
                ? new FeatureBundle.Finance(income, expenses, obligations) : null;
        var affordability = new AffordabilityRequest.Inputs(
                row.income(), row.housing(), row.mode(),
                itemized ? row.housingCost() : null,
                itemized ? row.groceriesCost() : null,
                itemized ? row.utilitiesCost() : null,
                itemized ? row.transportCost() : null,
                itemized ? row.otherLivingCosts() : null,
                itemized ? null : row.legacyLivingExpenses(), obligations,
                reference.rent(), reference.groceries(), reference.other(), reference.eligible(),
                new BigDecimal("9999999999.99"), row.revision(), row.generation());
        return new FeatureProviders.FinanceFeatures(legacyFinance, affordability);
    }

    private record FinanceRow(BigDecimal income, String mode, String housing, BigDecimal housingCost,
                              BigDecimal groceriesCost, BigDecimal utilitiesCost, BigDecimal transportCost,
                              BigDecimal otherLivingCosts, BigDecimal legacyLivingExpenses,
                              BigDecimal obligations, int revision, long generation) {}
}
