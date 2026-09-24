package com.rocketcredit.backend.affordability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.users.FinancialInputsService;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AffordabilityQueryService {
    private static final TypeReference<Map<String, BigDecimal>> BREAKDOWN = new TypeReference<>() {};
    private static final TypeReference<List<AffordabilityDtos.PartnerAmount>> PARTNERS = new TypeReference<>() {};
    private static final TypeReference<List<String>> REASONS = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final FinancialInputsService inputs;

    public AffordabilityQueryService(JdbcTemplate jdbc, ObjectMapper mapper, FinancialInputsService inputs) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.inputs = inputs;
    }

    public AffordabilityDtos.Estimate current(long userId) {
        inputs.current(userId);
        var state = jdbc.queryForMap("SELECT current_revision, generation FROM financial_input_state WHERE user_id=?", userId);
        long generation = ((Number) state.get("generation")).longValue();
        Integer revision = (Integer) state.get("current_revision");
        var snapshots = jdbc.query("""
                SELECT calculated_at, generation, financial_revision, formula_version, policy_version,
                       base_amount, monthly_payment_capacity, breakdown::text, partners::text, reasons::text
                FROM affordability_snapshots WHERE user_id=? ORDER BY calculated_at DESC, id DESC LIMIT 1
                """, AffordabilityQueryService::snapshot, userId);
        Snapshot snapshot = snapshots.isEmpty() ? null : snapshots.getFirst();
        String jobState = jdbc.query("SELECT state FROM affordability_jobs WHERE user_id=? AND generation=?",
                rs -> rs.next() ? rs.getString(1) : null, userId, generation);
        boolean fresh = snapshot != null && snapshot.generation() == generation;
        String status = fresh ? "READY" : switch (jobState == null ? "" : jobState) {
            case "QUEUED", "RUNNING" -> "UPDATING";
            case "FAILED" -> "ERROR";
            default -> "UNAVAILABLE";
        };
        return new AffordabilityDtos.Estimate(status, snapshot == null ? null : snapshot.calculatedAt(),
                generation, revision, snapshot == null ? null : snapshot.formulaVersion(),
                snapshot == null ? null : snapshot.policyVersion(), "USD", 6,
                snapshot == null ? null : snapshot.baseAmount(),
                snapshot == null ? null : snapshot.monthlyPaymentCapacity(),
                snapshot == null ? Map.of() : read(snapshot.breakdown(), BREAKDOWN),
                snapshot == null ? List.of() : read(snapshot.partners(), PARTNERS),
                snapshot == null ? List.of() : read(snapshot.reasons(), REASONS),
                snapshot != null && !fresh);
    }

    @Transactional(readOnly = true)
    public List<AffordabilityDtos.HistoryPoint> history(long userId, int months) {
        if (months < 1 || months > 24) throw ApiException.badRequest("INVALID_MONTHS", "months must be between 1 and 24");
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        YearMonth first = current.minusMonths(months - 1L);
        OffsetDateTime from = first.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        Map<YearMonth, AffordabilityDtos.HistoryPoint> latestByMonth = new LinkedHashMap<>();
        jdbc.query("""
                SELECT calculated_at, base_amount, formula_version, policy_version, data_source
                     , partners::text
                FROM affordability_snapshots WHERE user_id=? AND calculated_at>=?
                ORDER BY calculated_at DESC
                """, rs -> {
            OffsetDateTime at = rs.getObject("calculated_at", OffsetDateTime.class);
            YearMonth month = YearMonth.from(at.withOffsetSameInstant(ZoneOffset.UTC));
            latestByMonth.putIfAbsent(month, new AffordabilityDtos.HistoryPoint(month, at,
                    rs.getBigDecimal("base_amount"), partnerMap(read(rs.getString("partners"), PARTNERS)), rs.getString("formula_version"),
                    rs.getString("policy_version"), rs.getString("data_source")));
        }, userId, from);
        List<AffordabilityDtos.HistoryPoint> result = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth month = first.plusMonths(i);
            result.add(latestByMonth.getOrDefault(month,
                    new AffordabilityDtos.HistoryPoint(month, null, null, Map.of(), null, null, null)));
        }
        return result;
    }

    @Transactional
    public long recalculate(long userId) {
        inputs.current(userId);
        var state = jdbc.queryForMap("SELECT generation, current_revision FROM financial_input_state WHERE user_id=? FOR UPDATE", userId);
        long generation = ((Number) state.get("generation")).longValue();
        int revision = (Integer) state.get("current_revision");
        List<Long> existing = jdbc.query("SELECT id FROM affordability_jobs WHERE user_id=? AND generation=?",
                (rs, row) -> rs.getLong(1), userId, generation);
        if (!existing.isEmpty()) return existing.getFirst();
        return jdbc.queryForObject("""
                INSERT INTO affordability_jobs (user_id, generation, financial_revision)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, userId, generation, revision);
    }

    @Transactional(readOnly = true)
    public AffordabilityDtos.Job job(long userId, long id) {
        List<AffordabilityDtos.Job> found = jdbc.query("""
                SELECT id, state, attempt_count, failure_code, created_at, completed_at FROM (
                    SELECT id, state, attempt_count, failure_code, created_at, completed_at, 0 AS source_order
                    FROM affordability_jobs WHERE user_id=? AND id=?
                    UNION ALL
                    SELECT id, state, attempt_count, failure_code, created_at, completed_at, 1 AS source_order
                    FROM analysis_jobs WHERE user_id=? AND id=?
                ) jobs ORDER BY source_order LIMIT 1
                """, (rs, row) -> new AffordabilityDtos.Job(rs.getLong("id"), rs.getString("state"),
                rs.getInt("attempt_count"), rs.getString("failure_code"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class)), userId, id, userId, id);
        if (found.isEmpty()) throw ApiException.notFound("Analysis job");
        return found.getFirst();
    }

    private <T> T read(String json, TypeReference<T> type) {
        try { return mapper.readValue(json, type); }
        catch (IOException e) { throw new IllegalStateException("Stored affordability snapshot is invalid", e); }
    }

    private static Map<String, BigDecimal> partnerMap(List<AffordabilityDtos.PartnerAmount> partners) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        partners.forEach(partner -> amounts.put(partner.slug(), partner.possibleAmount()));
        return amounts;
    }

    private static Snapshot snapshot(ResultSet rs, int row) throws SQLException {
        return new Snapshot(rs.getObject("calculated_at", OffsetDateTime.class), rs.getLong("generation"),
                rs.getInt("financial_revision"), rs.getString("formula_version"), rs.getString("policy_version"),
                rs.getBigDecimal("base_amount"), rs.getBigDecimal("monthly_payment_capacity"),
                rs.getString("breakdown"), rs.getString("partners"), rs.getString("reasons"));
    }

    private record Snapshot(OffsetDateTime calculatedAt, long generation, int financialRevision,
                            String formulaVersion, String policyVersion, BigDecimal baseAmount,
                            BigDecimal monthlyPaymentCapacity, String breakdown, String partners, String reasons) {}
}
