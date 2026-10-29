// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.audit.AuditLog;
import com.rocketcredit.backend.auth.AuthenticatedUser;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.common.Paging;
import com.rocketcredit.backend.payments.CreditQuotes;
import com.rocketcredit.backend.payments.PaymentLedger;
import com.rocketcredit.backend.payments.RepaymentCalculator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human intervention in automated credit decisions (GDPR Art. 22(3); Directive (EU) 2023/2225, recital 56):
 * an administrator decides an application that the automated assessment left in REVIEW.
 *
 * <ul>
 *   <li>The automated decision (status, score, reasons, factors, snapshot) is never changed; the human
 *       decision is a separate, append-only application_reviews row shown next to it.</li>
 *   <li>Each application can be decided once. The row lock plus the UNIQUE(application_id) constraint make
 *       a repeated or concurrent submission answer 409 ALREADY_REVIEWED instead of deciding twice.</li>
 *   <li>An approval is at most the automated possible amount and at most the requested amount. The customer
 *       still has to accept it through the normal acceptance flow, which then uses the approved amount.</li>
 *   <li>The reviewer sees the outcome, amounts, reasons and factor scores. Factor details (which repeat
 *       income and cost figures), the feature snapshot, address, footprint report and consents stay hidden.</li>
 * </ul>
 */
@Service
public class ReviewService {
    private static final TypeReference<List<String>> LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CreditQuotes quotes;
    private final RepaymentCalculator calculator;
    private final PaymentLedger ledger;
    private final AuditLog audit;

    public ReviewService(JdbcTemplate jdbc, ObjectMapper mapper, CreditQuotes quotes, RepaymentCalculator calculator,
                         PaymentLedger ledger, AuditLog audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.quotes = quotes;
        this.calculator = calculator;
        this.ledger = ledger;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminDtos.Page<AdminDtos.ReviewItem> list(String state, int page, int size) {
        Paging.check(page, size);
        boolean open = state == null || state.isBlank() || "OPEN".equals(state);
        if (!open && !"DECIDED".equals(state)) throw ApiException.badRequest("INVALID_STATE", "Use state OPEN or DECIDED.");
        String where = " WHERE a.decision_status = 'REVIEW' AND r.id IS " + (open ? "NULL" : "NOT NULL");
        long total = jdbc.queryForObject("SELECT count(*) FROM credit_applications a LEFT JOIN application_reviews r ON r.application_id = a.id"
                + where, Long.class);
        var items = jdbc.query("""
                SELECT a.id, a.user_id, p.display_name AS partner_name, pr.name AS product_name, a.requested_amount,
                       a.possible_amount, a.score, a.created_at, r.outcome, r.approved_amount, r.created_at AS decided_at
                FROM credit_applications a JOIN partners p ON p.id = a.partner_id
                LEFT JOIN products pr ON pr.id = a.product_id LEFT JOIN application_reviews r ON r.application_id = a.id"""
                + where + (open ? " ORDER BY a.created_at, a.id" : " ORDER BY r.created_at DESC, a.id DESC") + " LIMIT ? OFFSET ?",
                (rs, n) -> new AdminDtos.ReviewItem(rs.getLong("id"), rs.getLong("user_id"), rs.getString("partner_name"),
                        rs.getString("product_name"), rs.getBigDecimal("requested_amount"), rs.getBigDecimal("possible_amount"),
                        rs.getBigDecimal("score"), rs.getObject("created_at", OffsetDateTime.class), rs.getString("outcome"),
                        rs.getBigDecimal("approved_amount"), rs.getObject("decided_at", OffsetDateTime.class)),
                size, (long) page * size);
        return new AdminDtos.Page<>(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public AdminDtos.ReviewDetail get(long applicationId) {
        var a = application(applicationId, false);
        AdminDtos.Plan plan = null;
        if (a.get("quote_id") != null) {
            var s = quotes.get(((Number) a.get("user_id")).longValue(), ((Number) a.get("quote_id")).longValue()).schedule();
            plan = new AdminDtos.Plan(s.termUnit(), s.installmentCount(), s.annualRate(), s.totalRepayment());
        }
        var decision = jdbc.query("""
                SELECT r.outcome, r.approved_amount, r.note, u.email, r.created_at
                FROM application_reviews r JOIN users u ON u.id = r.reviewer_id WHERE r.application_id = ?""",
                (rs, n) -> new AdminDtos.Decision(rs.getString("outcome"), rs.getBigDecimal("approved_amount"), rs.getString("note"),
                        rs.getString("email"), rs.getObject("created_at", OffsetDateTime.class)), applicationId)
                .stream().findFirst().orElse(null);
        boolean accepted = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM loans WHERE application_id = ?)",
                Boolean.class, applicationId));
        return new AdminDtos.ReviewDetail(applicationId, ((Number) a.get("user_id")).longValue(), (String) a.get("partner_name"),
                (String) a.get("product_name"), (BigDecimal) a.get("requested_amount"), (String) a.get("currency"),
                (String) a.get("decision_status"), (BigDecimal) a.get("score"), (BigDecimal) a.get("possible_amount"),
                read((String) a.get("reasons"), LIST), withoutDetails(read((String) a.get("factors"), MAP)),
                (String) a.get("ai_status"), (String) a.get("policy_version"), (String) a.get("model_version"),
                (OffsetDateTime) a.get("created_at"), plan, decision, accepted);
    }

    @Transactional
    public AdminDtos.ReviewDetail decide(AuthenticatedUser admin, long applicationId, AdminDtos.DecideReview body) {
        if (!Set.of("APPROVED", "REJECTED").contains(body.outcome()))
            throw ApiException.badRequest("INVALID_OUTCOME", "Choose APPROVED or REJECTED.");
        String note = body.note().trim();
        if (note.length() < 10 || note.length() > 500)
            throw ApiException.badRequest("INVALID_NOTE", "Write a note of 10 to 500 characters explaining the decision.");
        var a = application(applicationId, true);
        Long existing = jdbc.query("SELECT id FROM application_reviews WHERE application_id = ?", (rs, n) -> rs.getLong(1), applicationId)
                .stream().findFirst().orElse(null);
        if (existing != null) throw alreadyReviewed();
        BigDecimal approved = null;
        if ("APPROVED".equals(body.outcome())) {
            approved = body.approvedAmount();
            BigDecimal possible = (BigDecimal) a.get("possible_amount");
            BigDecimal requested = (BigDecimal) a.get("requested_amount");
            if (approved == null || approved.signum() <= 0 || approved.compareTo(possible) > 0 || approved.compareTo(requested) > 0)
                throw ApiException.badRequest("INVALID_APPROVED_AMOUNT", "The approved amount must be above 0 and at most "
                        + possible.min(requested).toPlainString() + " USD (the possible and the requested amount).");
            if (a.get("quote_id") == null)
                throw ApiException.conflict("NO_REPAYMENT_PLAN", "This application has no repayment plan, so it cannot be approved.");
            // The customer's term must still produce a valid schedule for the approved amount.
            var s = quotes.get(((Number) a.get("user_id")).longValue(), ((Number) a.get("quote_id")).longValue()).schedule();
            calculator.calculate(approved, s.termUnit(), s.installmentCount(), ledger.today());
        } else if (body.approvedAmount() != null) {
            throw ApiException.badRequest("INVALID_APPROVED_AMOUNT", "A rejection has no approved amount.");
        }
        try {
            jdbc.update("INSERT INTO application_reviews(application_id, reviewer_id, outcome, approved_amount, note) VALUES (?, ?, ?, ?, ?)",
                    applicationId, admin.getId(), body.outcome(), approved, note);
        } catch (DuplicateKeyException e) {
            throw alreadyReviewed();
        }
        var details = new LinkedHashMap<String, Object>();
        details.put("outcome", body.outcome());
        if (approved != null) details.put("approvedAmount", approved.toPlainString());
        audit.record(admin, "ADMIN_REVIEW_DECIDED", "APPLICATION", applicationId, details); // the note stays in application_reviews
        return get(applicationId);
    }

    private static ApiException alreadyReviewed() {
        return ApiException.conflict("ALREADY_REVIEWED", "This application has already been decided.");
    }

    /** A REVIEW application, optionally locked for the decision. Other applications are not reviewable (404). */
    private Map<String, Object> application(long id, boolean lock) {
        var rows = jdbc.queryForList("""
                SELECT a.id, a.user_id, a.quote_id, a.requested_amount, a.currency, a.decision_status, a.score, a.possible_amount,
                       a.reasons::text AS reasons, a.factors::text AS factors, a.ai_status, a.policy_version, a.model_version,
                       a.created_at, p.display_name AS partner_name, pr.name AS product_name
                FROM credit_applications a JOIN partners p ON p.id = a.partner_id LEFT JOIN products pr ON pr.id = a.product_id
                WHERE a.id = ? AND a.decision_status = 'REVIEW'""" + (lock ? " FOR UPDATE OF a" : ""), id);
        if (rows.isEmpty()) throw ApiException.notFound("Review");
        var row = rows.getFirst();
        row.put("created_at", toOffset(row.get("created_at")));
        return row;
    }

    private static OffsetDateTime toOffset(Object value) {
        return value instanceof java.sql.Timestamp t ? t.toInstant().atOffset(java.time.ZoneOffset.UTC) : (OffsetDateTime) value;
    }

    /** Keeps each factor's score, weight and reason codes; drops "details", which repeats raw financial figures. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withoutDetails(Map<String, Object> factors) {
        var out = new LinkedHashMap<String, Object>();
        factors.forEach((name, value) -> {
            if (value instanceof Map<?, ?> m) {
                var copy = new LinkedHashMap<>((Map<String, Object>) m);
                copy.remove("details");
                out.put(name, copy);
            } else {
                out.put(name, value);
            }
        });
        return out;
    }

    private <T> T read(String json, TypeReference<T> type) {
        try { return mapper.readValue(json, type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
