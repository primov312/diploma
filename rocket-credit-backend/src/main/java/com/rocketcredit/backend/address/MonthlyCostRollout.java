package com.rocketcredit.backend.address;

import com.rocketcredit.backend.affordability.AffordabilityJobs;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Initializes saved addresses once after the first manual dataset import; never adopts refreshes silently. */
@Component
public class MonthlyCostRollout {
    private final JdbcTemplate jdbc;
    private final MonthlyCostResolver costs;
    private final AffordabilityJobs jobs;
    private final TransactionTemplate transactions;
    public MonthlyCostRollout(JdbcTemplate jdbc, MonthlyCostResolver costs, AffordabilityJobs jobs, TransactionTemplate transactions) {
        this.jdbc = jdbc; this.costs = costs; this.jobs = jobs; this.transactions = transactions;
    }

    @Scheduled(initialDelayString = "${rocket.monthly-costs.rollout-delay:3000}", fixedDelayString = "${rocket.monthly-costs.rollout-delay:3000}")
    public void initialize() {
        if (costs.activeDataset() == null) return;
        var users = jdbc.query("""
                SELECT a.user_id FROM user_address_state a JOIN financial_input_state f ON f.user_id=a.user_id
                LEFT JOIN affordability_jobs j ON j.user_id=f.user_id AND j.generation=f.generation
                WHERE NOT EXISTS(SELECT 1 FROM user_living_cost_profiles p WHERE p.user_id=a.user_id)
                    OR j.local_cost_context IS NULL OR j.local_cost_context->>'methodVersion' IS DISTINCT FROM 'automatic-living-costs-v1'
                ORDER BY a.user_id LIMIT 25
                """, (rs, row) -> rs.getLong(1));
        for (Long user : users) transactions.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT generation FROM financial_input_state WHERE user_id=? FOR UPDATE", Long.class, user);
            var state = jdbc.queryForMap("""
                    SELECT f.generation,j.local_cost_context FROM financial_input_state f
                    LEFT JOIN affordability_jobs j ON j.user_id=f.user_id AND j.generation=f.generation WHERE f.user_id=?
                    """, user);
            if (costs.savedProfile(user) == null || state.get("local_cost_context") == null
                    || !MonthlyCostResolver.METHOD_VERSION.equals(costs.read(state.get("local_cost_context").toString()).path("methodVersion").asText())) {
                costs.ensureDefaultProfile(user);
                jobs.recalculate(user);
            }
        });
    }
}
