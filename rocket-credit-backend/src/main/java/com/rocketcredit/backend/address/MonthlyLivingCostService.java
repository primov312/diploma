package com.rocketcredit.backend.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rocketcredit.backend.affordability.AffordabilityJobs;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.users.FinancialInputsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyLivingCostService {
    private final MonthlyCostResolver costs;
    private final FinancialInputsService finances;
    private final AffordabilityJobs jobs;
    private final JdbcTemplate jdbc;

    public MonthlyLivingCostService(MonthlyCostResolver costs, FinancialInputsService finances, AffordabilityJobs jobs, JdbcTemplate jdbc) {
        this.costs = costs; this.finances = finances; this.jobs = jobs; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ObjectNode current(long userId) { return costs.quote(userId, null, null, false); }

    @Transactional(readOnly = true)
    public ObjectNode preview(long userId, JsonNode request) {
        if (!request.isObject()) throw ApiException.badRequest("INVALID_PROFILE", "Expected a monthly cost profile.");
        String postalCode = request.hasNonNull("postalCode") ? request.path("postalCode").asText() : null;
        if (postalCode != null) costs.postalCode(postalCode);
        return costs.quote(userId, postalCode, request, request.path("refreshDataset").asBoolean());
    }

    @Transactional
    public ObjectNode save(long userId, JsonNode request) {
        finances.current(userId);
        jdbc.queryForObject("SELECT generation FROM financial_input_state WHERE user_id=? FOR UPDATE", Long.class, userId);
        if (jdbc.queryForObject("SELECT count(*) FROM user_address_state WHERE user_id=?", Integer.class, userId) == 0) {
            throw ApiException.badRequest("SAVE_ADDRESS_FIRST", "Save your living address before saving these assumptions.");
        }
        var profile = costs.savedProfile(userId);
        int revision = profile == null ? 0 : profile.revision();
        JsonNode expected = request.get("expectedRevision");
        if (expected == null || !expected.isIntegralNumber() || !expected.canConvertToInt() || expected.asInt() != revision) {
            throw ApiException.conflict("REVISION_CONFLICT", "Monthly assumptions changed in another session. Reload them before saving.");
        }
        var dataset = profile == null || request.path("refreshDataset").asBoolean()
                ? costs.activeDataset() : costs.dataset(profile.datasetVersion());
        if (dataset == null) throw ApiException.badRequest("MONTHLY_DATASET_UNAVAILABLE", "Monthly price references are unavailable.");
        var updated = costs.validateProfile(request, dataset, revision + 1);
        costs.quote(userId, null, request, request.path("refreshDataset").asBoolean()); // validate derived totals before publishing
        costs.insertProfile(userId, updated);
        long jobId = jobs.recalculate(userId);
        ObjectNode result = current(userId);
        result.put("recalculationJobId", jobId);
        return result;
    }
}
