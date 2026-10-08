package com.rocketcredit.backend.users;

import com.rocketcredit.backend.common.ApiException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialInputsService {
    private final JdbcTemplate jdbc;
    private final com.rocketcredit.backend.affordability.AffordabilityJobs jobs;
    private final DemoFinancialProfileRepository profiles;

    public FinancialInputsService(JdbcTemplate jdbc, DemoFinancialProfileRepository profiles, com.rocketcredit.backend.affordability.AffordabilityJobs jobs) {
        this.jdbc = jdbc;
        this.jobs = jobs;
        this.profiles = profiles;
    }

    @Transactional
    public FinancialInputsDto current(long userId) {
        ensureInitial(userId);
        return jdbc.queryForObject("""
                SELECT r.revision, r.monthly_net_income, r.housing_situation, r.expense_mode,
                       r.housing_cost, r.groceries_cost, r.utilities_cost, r.transport_cost,
                       r.other_living_costs, r.legacy_living_expenses, r.monthly_obligations,
                       r.source, r.created_at
                FROM financial_input_state s
                JOIN financial_input_revisions r
                  ON r.user_id = s.user_id AND r.revision = s.current_revision
                WHERE s.user_id = ?
                """, FinancialInputsService::map, userId);
    }

    @Transactional
    public FinancialInputsSaveResult save(long userId, SaveFinancialInputsRequest request) {
        ensureInitial(userId);
        Map<String, Object> state = jdbc.queryForMap(
                "SELECT current_revision, generation FROM financial_input_state WHERE user_id = ? FOR UPDATE", userId);
        Integer current = (Integer) state.get("current_revision");
        long generation = ((Number) state.get("generation")).longValue();
        if (current == null || current.intValue() != request.expectedRevision()) {
            throw ApiException.conflict("REVISION_CONFLICT", "Financial information changed in another session. Reload and review the latest values.");
        }

        validate(request);
        var trusted = profiles.findById(userId).orElseThrow(() -> ApiException.notFound("Financial profile"));
        int revision = current + 1;
        String source = "AUTOMATIC";
        jdbc.update("""
                INSERT INTO financial_input_revisions (
                    user_id, revision, monthly_net_income, housing_situation, expense_mode,
                    housing_cost, groceries_cost, utilities_cost, transport_cost,
                    other_living_costs, legacy_living_expenses, monthly_obligations, source
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, revision, trusted.getMonthlyIncome(), request.housingSituation().name(),
                request.expenseMode().name(), request.housingCost(), request.groceriesCost(),
                request.utilitiesCost(), request.transportCost(), request.otherLivingCosts(),
                trusted.getMonthlyExpenses(), trusted.getMonthlyObligations(), source);
        long nextGeneration = generation + 1;
        jdbc.update("UPDATE financial_input_state SET current_revision = ?, generation = ? WHERE user_id = ?",
                revision, nextGeneration, userId);
        long jobId = jobs.enqueue(userId, nextGeneration, revision);
        return new FinancialInputsSaveResult(current(userId), nextGeneration, jobId);
    }

    private void ensureInitial(long userId) {
        var profile = profiles.findById(userId).orElseThrow(() -> ApiException.notFound("Financial profile"));
        jdbc.update("""
                INSERT INTO financial_input_revisions
                    (user_id, revision, monthly_net_income, housing_situation, expense_mode,
                     legacy_living_expenses, monthly_obligations, source)
                VALUES (?, 1, ?, 'OTHER', 'AUTOMATIC', ?, ?, 'AUTOMATIC')
                ON CONFLICT (user_id, revision) DO NOTHING
                """, userId, profile.getMonthlyIncome(), profile.getMonthlyExpenses(), profile.getMonthlyObligations());
        jdbc.update("""
                INSERT INTO financial_input_state (user_id, current_revision) VALUES (?, 1)
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
        var state = jdbc.queryForMap("SELECT generation,current_revision FROM financial_input_state WHERE user_id=?", userId);
        jobs.enqueue(userId, ((Number) state.get("generation")).longValue(), ((Number) state.get("current_revision")).intValue());
    }

    private static void validate(SaveFinancialInputsRequest r) {
        if (r.expenseMode() != SaveFinancialInputsRequest.ExpenseMode.AUTOMATIC) {
            throw ApiException.badRequest("AUTOMATIC_COSTS_REQUIRED", "Living expenses are calculated automatically.");
        }
        for (BigDecimal amount : new BigDecimal[]{r.monthlyNetIncome(), r.monthlyObligations(), r.housingCost(),
                r.groceriesCost(), r.utilitiesCost(), r.transportCost(), r.otherLivingCosts(), r.legacyLivingExpenses()}) {
            if (amount != null) throw ApiException.badRequest("MANAGED_FINANCIAL_AMOUNT", "Financial amounts are supplied by the demo profile and pricing datasets.");
        }
    }

    private static FinancialInputsDto map(ResultSet rs, int row) throws SQLException {
        return new FinancialInputsDto(rs.getInt("revision"), rs.getBigDecimal("monthly_net_income"),
                rs.getString("housing_situation"), rs.getString("expense_mode"),
                rs.getBigDecimal("housing_cost"), rs.getBigDecimal("groceries_cost"),
                rs.getBigDecimal("utilities_cost"), rs.getBigDecimal("transport_cost"),
                rs.getBigDecimal("other_living_costs"), rs.getBigDecimal("legacy_living_expenses"),
                rs.getBigDecimal("monthly_obligations"), rs.getString("source"),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
