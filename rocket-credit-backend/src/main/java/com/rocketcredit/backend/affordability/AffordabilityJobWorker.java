package com.rocketcredit.backend.affordability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.analysis.AffordabilityRequest;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.analysis.AnalysisUnavailableException;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.partners.PartnerRepository;
import com.rocketcredit.backend.users.FinancialInputsDto;
import com.rocketcredit.backend.users.FinancialInputsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded database-backed worker. Leases recover jobs left RUNNING by a restart. */
@Component
public class AffordabilityJobWorker {
    private static final Logger log = LoggerFactory.getLogger(AffordabilityJobWorker.class);
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");
    private static final TypeReference<Map<String, BigDecimal>> BREAKDOWN = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final FinancialInputsService inputs;
    private final PartnerRepository partners;
    private final AnalysisClient analysis;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public AffordabilityJobWorker(JdbcTemplate jdbc, FinancialInputsService inputs,
                                  PartnerRepository partners, AnalysisClient analysis, ObjectMapper mapper,
                                  TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.inputs = inputs;
        this.partners = partners;
        this.analysis = analysis;
        this.mapper = mapper;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${rocket.affordability.poll-delay:1000}")
    public void poll() {
        for (int count = 0; count < 4; count++) {
            JobRow job = claim();
            if (job == null) return;
            calculate(job);
        }
    }

    protected JobRow claim() {
        return transactions.execute(status -> claimLocked());
    }

    private JobRow claimLocked() {
        var jobs = jdbc.query("""
                SELECT id, user_id, generation, financial_revision, attempt_count
                FROM affordability_jobs
                WHERE state = 'QUEUED' OR (state = 'RUNNING' AND lease_until < now())
                ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1
                """, (rs, row) -> new JobRow(rs.getLong("id"), rs.getLong("user_id"),
                rs.getLong("generation"), rs.getInt("financial_revision"), rs.getInt("attempt_count")));
        if (jobs.isEmpty()) return null;
        JobRow job = jobs.getFirst();
        Long currentGeneration = jdbc.queryForObject(
                "SELECT generation FROM financial_input_state WHERE user_id = ?", Long.class, job.userId());
        if (currentGeneration == null || currentGeneration.longValue() != job.generation()) {
            jdbc.update("UPDATE affordability_jobs SET state='SUPERSEDED', completed_at=now() WHERE id=?", job.id());
            return job.withState("SUPERSEDED");
        }
        if (job.attemptCount() >= 3) {
            jdbc.update("UPDATE affordability_jobs SET state='FAILED', failure_code='RETRY_EXHAUSTED', completed_at=now() WHERE id=?", job.id());
            return job.withState("FAILED");
        }
        jdbc.update("UPDATE affordability_jobs SET state='RUNNING', attempt_count=attempt_count+1, lease_until=now()+interval '45 seconds' WHERE id=?", job.id());
        return job;
    }

    private void calculate(JobRow job) {
        if (job.terminal()) return;
        try {
            FinancialInputsDto input = inputs.current(job.userId());
            CostReference reference = eligibleReference(job.userId());
            AffordabilityRequest.Inputs requestInputs = new AffordabilityRequest.Inputs(
                    input.monthlyNetIncome(), input.housingSituation(), input.expenseMode(), input.housingCost(),
                    input.groceriesCost(), input.utilitiesCost(), input.transportCost(), input.otherLivingCosts(),
                    input.legacyLivingExpenses(), input.monthlyObligations(), reference.rent(), reference.groceries(),
                    reference.eligible(), MAX_MONEY,
                    input.revision(), job.generation());
            var result = analysis.affordability(new AffordabilityRequest(requestInputs));
            if (result.baseAmount() != null && result.baseAmount().compareTo(MAX_MONEY) > 0) {
                fail(job.id(), "CALCULATED_AMOUNT_OVERFLOW", false);
                return;
            }
            List<AffordabilityDtos.PartnerAmount> partnerAmounts = new ArrayList<>();
            for (var partner : partners.findAll()) {
                BigDecimal amount = result.baseAmount() == null ? null : result.baseAmount().min(partner.getAmountCap())
                        .setScale(2, RoundingMode.DOWN);
                partnerAmounts.add(new AffordabilityDtos.PartnerAmount(partner.getSlug(), partner.getAmountCap(), amount));
            }
            save(job, result, partnerAmounts);
        } catch (ApiException | AnalysisUnavailableException e) {
            log.warn("affordability job {} attempt failed: {}", job.id(), e.getMessage());
            fail(job.id(), "ANALYSIS_UNAVAILABLE", true);
        } catch (Exception e) {
            log.error("affordability job {} failed", job.id(), e);
            fail(job.id(), "CALCULATION_FAILED", true);
        }
    }

    protected void save(JobRow job, com.rocketcredit.backend.analysis.AffordabilityResult result,
                        List<AffordabilityDtos.PartnerAmount> partnerAmounts) throws Exception {
        transactions.executeWithoutResult(status -> {
            try {
                saveLocked(job, result, partnerAmounts);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void saveLocked(JobRow job, com.rocketcredit.backend.analysis.AffordabilityResult result,
                            List<AffordabilityDtos.PartnerAmount> partnerAmounts) throws Exception {
        Long generation = jdbc.queryForObject("SELECT generation FROM financial_input_state WHERE user_id=?", Long.class, job.userId());
        if (generation == null || generation.longValue() != job.generation()) {
            jdbc.update("UPDATE affordability_jobs SET state='SUPERSEDED', completed_at=now(), lease_until=NULL WHERE id=?", job.id());
            return;
        }
        jdbc.update("""
                INSERT INTO affordability_snapshots (user_id, generation, financial_revision, formula_version,
                    policy_version, base_amount, monthly_payment_capacity, breakdown, partners, reasons, data_source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
                ON CONFLICT (user_id, generation, formula_version) DO NOTHING
                """, job.userId(), job.generation(), job.revision(), result.formulaVersion(), result.policyVersion(),
                result.baseAmount(), result.monthlyPaymentCapacity(), mapper.writeValueAsString(result.breakdown()),
                mapper.writeValueAsString(partnerAmounts), mapper.writeValueAsString(result.reasons()), inputSource(job.userId()));
        jdbc.update("UPDATE affordability_jobs SET state='SUCCEEDED', completed_at=now(), lease_until=NULL WHERE id=?", job.id());
    }

    protected void fail(long id, String code, boolean retryable) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                UPDATE affordability_jobs SET state = CASE WHEN ? AND attempt_count < 3 THEN 'QUEUED' ELSE 'FAILED' END,
                    failure_code=?, completed_at=CASE WHEN ? AND attempt_count < 3 THEN NULL ELSE now() END,
                    lease_until=NULL WHERE id=?
                """, retryable, code, retryable, id));
    }

    private CostReference eligibleReference(long userId) {
        List<CostReference> found = jdbc.query("""
                SELECT c.monthly_rent, c.monthly_groceries
                FROM user_address_state s
                JOIN user_address_revisions a ON a.user_id=s.user_id AND a.revision=s.current_revision
                JOIN LATERAL (
                    SELECT status FROM address_verifications v
                    WHERE v.user_id=a.user_id AND v.address_revision=a.revision
                    ORDER BY v.created_at DESC LIMIT 1
                ) verification ON verification.status='VERIFIED_DEMO'
                JOIN LATERAL (
                    SELECT monthly_rent, monthly_groceries FROM local_cost_references
                    WHERE district_id=a.district_id ORDER BY observed_at DESC LIMIT 1
                ) c ON TRUE
                WHERE s.user_id=?
                """, (rs, row) -> new CostReference(rs.getBigDecimal("monthly_rent"),
                rs.getBigDecimal("monthly_groceries"), true), userId);
        return found.isEmpty() ? new CostReference(null, null, false) : found.getFirst();
    }

    private String inputSource(long userId) {
        String source = jdbc.queryForObject("""
                SELECT r.source FROM financial_input_state s JOIN financial_input_revisions r
                  ON r.user_id=s.user_id AND r.revision=s.current_revision WHERE s.user_id=?
                """, String.class, userId);
        return "USER_DECLARED".equals(source) ? "USER_DECLARED" : "SYNTHETIC";
    }

    private record CostReference(BigDecimal rent, BigDecimal groceries, boolean eligible) {}

    public record JobRow(long id, long userId, long generation, int revision, int attemptCount, String state) {
        JobRow(long id, long userId, long generation, int revision, int attemptCount) {
            this(id, userId, generation, revision, attemptCount, "RUNNING");
        }
        JobRow withState(String value) { return new JobRow(id, userId, generation, revision, attemptCount, value); }
        boolean terminal() { return !"RUNNING".equals(state); }
    }
}
