package com.rocketcredit.backend.users;

import com.rocketcredit.backend.common.ApiException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialInputsService {
    private final JdbcTemplate jdbc;
    private final DemoFinancialProfileRepository profiles;

    public FinancialInputsService(JdbcTemplate jdbc, DemoFinancialProfileRepository profiles) {
        this.jdbc = jdbc;
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
        int revision = current + 1;
        String source = "USER_DECLARED";
        jdbc.update("""
                INSERT INTO financial_input_revisions (
                    user_id, revision, monthly_net_income, housing_situation, expense_mode,
                    housing_cost, groceries_cost, utilities_cost, transport_cost,
                    other_living_costs, legacy_living_expenses, monthly_obligations, source
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, revision, request.monthlyNetIncome(), request.housingSituation().name(),
                request.expenseMode().name(), request.housingCost(), request.groceriesCost(),
                request.utilitiesCost(), request.transportCost(), request.otherLivingCosts(),
                request.legacyLivingExpenses(), request.monthlyObligations(), source);
        long nextGeneration = generation + 1;
        jdbc.update("UPDATE financial_input_state SET current_revision = ?, generation = ? WHERE user_id = ?",
                revision, nextGeneration, userId);
        var key = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO affordability_jobs (user_id, generation, financial_revision) VALUES (?, ?, ?)",
                    new String[]{"id"});
            statement.setLong(1, userId);
            statement.setLong(2, nextGeneration);
            statement.setInt(3, revision);
            return statement;
        }, key);
        long jobId = key.getKey().longValue();
        return new FinancialInputsSaveResult(current(userId), nextGeneration, jobId);
    }

    private void ensureInitial(long userId) {
        var profile = profiles.findById(userId).orElseThrow(() -> ApiException.notFound("Financial profile"));
        jdbc.update("""
                INSERT INTO financial_input_revisions
                    (user_id, revision, monthly_net_income, housing_situation, expense_mode,
                     legacy_living_expenses, monthly_obligations, source)
                VALUES (?, 1, ?, 'OTHER', 'AGGREGATE', ?, ?, ?)
                ON CONFLICT (user_id, revision) DO NOTHING
                """, userId, profile.getMonthlyIncome(), profile.getMonthlyExpenses(),
                profile.getMonthlyObligations(), profile.getSyntheticSource().name());
        jdbc.update("""
                INSERT INTO financial_input_state (user_id, current_revision) VALUES (?, 1)
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
        jdbc.update("""
                INSERT INTO affordability_jobs (user_id, generation, financial_revision)
                SELECT user_id, generation, current_revision FROM financial_input_state WHERE user_id=?
                ON CONFLICT (user_id, generation) DO NOTHING
                """, userId);
    }

    private static void validate(SaveFinancialInputsRequest r) {
        if (r.monthlyNetIncome() != null && r.monthlyNetIncome().signum() < 0 ||
                r.monthlyObligations().signum() < 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Financial amounts must be zero or greater");
        }
        if (r.expenseMode() == SaveFinancialInputsRequest.ExpenseMode.AGGREGATE) {
            if (r.legacyLivingExpenses() == null || any(r.housingCost(), r.groceriesCost(), r.utilitiesCost(),
                    r.transportCost(), r.otherLivingCosts())) {
                throw ApiException.badRequest("INVALID_EXPENSES", "Aggregate mode requires only legacyLivingExpenses");
            }
        } else if (r.legacyLivingExpenses() != null || anyNull(r.housingCost(), r.groceriesCost(),
                r.utilitiesCost(), r.transportCost(), r.otherLivingCosts())) {
            throw ApiException.badRequest("INVALID_EXPENSES", "Itemized mode requires all five expense categories and no aggregate total");
        }
        for (BigDecimal amount : new BigDecimal[]{r.housingCost(), r.groceriesCost(), r.utilitiesCost(),
                r.transportCost(), r.otherLivingCosts(), r.legacyLivingExpenses()}) {
            if (amount != null && amount.signum() < 0) {
                throw ApiException.badRequest("INVALID_AMOUNT", "Financial amounts must be zero or greater");
            }
        }
    }

    private static boolean any(BigDecimal... values) {
        for (BigDecimal value : values) if (value != null) return true;
        return false;
    }

    private static boolean anyNull(BigDecimal... values) {
        for (BigDecimal value : values) if (value == null) return true;
        return false;
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
