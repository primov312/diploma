package com.rocketcredit.backend.affordability;

import com.rocketcredit.backend.address.MonthlyCostResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every enqueue pins the researched references while holding the financial state lock. */
@Service
public class AffordabilityJobs {
    private final JdbcTemplate jdbc;
    private final MonthlyCostResolver costs;

    public AffordabilityJobs(JdbcTemplate jdbc, MonthlyCostResolver costs) {
        this.jdbc = jdbc;
        this.costs = costs;
    }

    @Transactional
    public long enqueue(long userId, long generation, int revision) {
        jdbc.queryForObject("SELECT generation FROM financial_input_state WHERE user_id=? FOR UPDATE", Long.class, userId);
        Long existing = jdbc.query("SELECT id FROM affordability_jobs WHERE user_id=? AND generation=?",
                rs -> rs.next() ? rs.getLong(1) : null, userId, generation);
        if (existing != null) return existing;
        String context = costs.capture(userId).toString();
        return jdbc.queryForObject("""
                INSERT INTO affordability_jobs(user_id,generation,financial_revision,local_cost_context)
                VALUES(?,?,?,?::jsonb) RETURNING id
                """, Long.class, userId, generation, revision, context);
    }

    @Transactional
    public long recalculate(long userId) {
        var state = jdbc.queryForMap("SELECT current_revision,generation FROM financial_input_state WHERE user_id=? FOR UPDATE", userId);
        long generation = ((Number) state.get("generation")).longValue() + 1;
        jdbc.update("UPDATE financial_input_state SET generation=? WHERE user_id=?", generation, userId);
        return enqueue(userId, generation, ((Number) state.get("current_revision")).intValue());
    }
}
