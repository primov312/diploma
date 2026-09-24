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

    public DbFinanceFeatureProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
                SELECT r.*, s.generation, c.monthly_rent AS district_rent_reference,
                       c.monthly_groceries AS district_grocery_reference,
                       (v.status = 'VERIFIED_DEMO' AND c.district_id IS NOT NULL) AS references_eligible
                FROM financial_input_state s
                JOIN financial_input_revisions r ON r.user_id=s.user_id AND r.revision=s.current_revision
                LEFT JOIN user_address_state address_state ON address_state.user_id=s.user_id
                LEFT JOIN user_address_revisions address ON address.user_id=address_state.user_id
                  AND address.revision=address_state.current_revision
                LEFT JOIN LATERAL (
                    SELECT status FROM address_verifications verification
                    WHERE verification.user_id=address.user_id AND verification.address_revision=address.revision
                    ORDER BY created_at DESC LIMIT 1
                ) v ON TRUE
                LEFT JOIN LATERAL (
                    SELECT district_id, monthly_rent, monthly_groceries FROM local_cost_references
                    WHERE district_id=address.district_id ORDER BY observed_at DESC LIMIT 1
                ) c ON v.status='VERIFIED_DEMO'
                WHERE s.user_id=?
                """, (rs, row) -> new FinanceRow(rs.getBigDecimal("monthly_net_income"),
                rs.getString("expense_mode"), rs.getString("housing_situation"), rs.getBigDecimal("housing_cost"),
                rs.getBigDecimal("groceries_cost"), rs.getBigDecimal("utilities_cost"),
                rs.getBigDecimal("transport_cost"), rs.getBigDecimal("other_living_costs"),
                rs.getBigDecimal("legacy_living_expenses"), rs.getBigDecimal("monthly_obligations"),
                rs.getBigDecimal("district_rent_reference"), rs.getBigDecimal("district_grocery_reference"),
                rs.getBoolean("references_eligible"), rs.getInt("revision"), rs.getLong("generation")), ctx.userId());
        if (rows.isEmpty()) return new FeatureProviders.FinanceFeatures(null, null);
        FinanceRow row = rows.getFirst();
        BigDecimal income = row.income() == null ? BigDecimal.ZERO : row.income();
        BigDecimal obligations = row.obligations();
        boolean itemized = "ITEMIZED".equals(row.mode());
        BigDecimal expenses;
        if (itemized) {
            BigDecimal rent = row.housingCost();
            BigDecimal groceries = row.groceriesCost();
            if (row.referencesEligible()) {
                if ("RENTING".equals(row.housing()) && row.districtRentReference() != null) rent = rent.max(row.districtRentReference());
                if (row.districtGroceryReference() != null) groceries = groceries.max(row.districtGroceryReference());
            }
            expenses = rent.add(groceries).add(row.utilitiesCost()).add(row.transportCost()).add(row.otherLivingCosts());
        } else {
            expenses = row.legacyLivingExpenses();
            if (row.referencesEligible()) {
                BigDecimal floor = "RENTING".equals(row.housing()) && row.districtRentReference() != null
                        ? row.districtRentReference() : BigDecimal.ZERO;
                if (row.districtGroceryReference() != null) floor = floor.add(row.districtGroceryReference());
                expenses = expenses.max(floor);
            }
        }
        FeatureBundle.Finance legacyFinance = row.income() != null
                ? new FeatureBundle.Finance(income, expenses, obligations) : null;
        var affordability = new AffordabilityRequest.Inputs(
                row.income(), row.housing(), row.mode(),
                itemized ? row.housingCost() : null,
                itemized ? row.groceriesCost() : null,
                itemized ? row.utilitiesCost() : null,
                itemized ? row.transportCost() : null,
                itemized ? row.otherLivingCosts() : null,
                itemized ? null : row.legacyLivingExpenses(), obligations,
                row.districtRentReference(), row.districtGroceryReference(), row.referencesEligible(),
                new BigDecimal("9999999999.99"), row.revision(), row.generation());
        return new FeatureProviders.FinanceFeatures(legacyFinance, affordability);
    }

    private record FinanceRow(BigDecimal income, String mode, String housing, BigDecimal housingCost,
                              BigDecimal groceriesCost, BigDecimal utilitiesCost, BigDecimal transportCost,
                              BigDecimal otherLivingCosts, BigDecimal legacyLivingExpenses,
                              BigDecimal obligations, BigDecimal districtRentReference,
                              BigDecimal districtGroceryReference, boolean referencesEligible,
                              int revision, long generation) {}
}
