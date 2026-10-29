// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.audit.AuditLog;
import com.rocketcredit.backend.auth.AuthenticatedUser;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.common.Paging;
import com.rocketcredit.backend.payments.RepaymentCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform overview, partner settings and the read-only audit log. */
@Service
public class AdminPlatformService {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final HealthEndpoint health;
    private final ObjectMapper mapper;
    private final AuditLog audit;

    public AdminPlatformService(JdbcTemplate jdbc, HealthEndpoint health, ObjectMapper mapper, AuditLog audit) {
        this.jdbc = jdbc;
        this.health = health;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminDtos.Overview overview() {
        Map<String, Map<String, Long>> users = new LinkedHashMap<>();
        for (String role : List.of("CUSTOMER", "SELLER", "ADMIN")) users.put(role, new LinkedHashMap<>(Map.of("enabled", 0L, "disabled", 0L)));
        jdbc.query("SELECT role, enabled, count(*) FROM users GROUP BY role, enabled",
                rs -> { users.get(rs.getString(1)).put(rs.getBoolean(2) ? "enabled" : "disabled", rs.getLong(3)); });
        Map<String, Long> decisions = new LinkedHashMap<>();
        for (String status : List.of("APPROVED", "REVIEW", "REJECTED")) decisions.put(status, 0L);
        jdbc.query("SELECT decision_status, count(*) FROM credit_applications GROUP BY decision_status",
                rs -> { decisions.put(rs.getString(1), rs.getLong(2)); });
        var reviews = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE r.id IS NULL) AS open, count(*) FILTER (WHERE r.outcome = 'APPROVED') AS approved,
                       count(*) FILTER (WHERE r.outcome = 'REJECTED') AS rejected
                FROM credit_applications a LEFT JOIN application_reviews r ON r.application_id = a.id
                WHERE a.decision_status = 'REVIEW'""");
        Map<String, Long> reviewCounts = new LinkedHashMap<>();
        reviews.forEach((k, v) -> reviewCounts.put(k, ((Number) v).longValue()));
        long activeLoans = jdbc.queryForObject("SELECT count(*) FROM loans WHERE status = 'ACTIVE'", Long.class);
        long overdue = jdbc.queryForObject("""
                SELECT count(*) FROM repayment_installments i JOIN loans l ON l.id = i.loan_id
                WHERE l.status = 'ACTIVE' AND i.paid_at IS NULL AND i.due_date < ?""", Long.class,
                LocalDate.now(RepaymentCalculator.ZONE));
        // Reuses the actuator health indicators (the same ones /actuator/health shows).
        Map<String, String> services = new LinkedHashMap<>();
        services.put("backend", "UP");
        services.put("database", status("db"));
        services.put("analysis", status("analysis"));
        services.put("collector", status("collector"));
        return new AdminDtos.Overview(users, decisions, reviewCounts, activeLoans, overdue, services);
    }

    private String status(String indicator) {
        try {
            HealthComponent component = health.healthForPath(indicator);
            return component == null ? "UNKNOWN" : component.getStatus().getCode();
        } catch (RuntimeException e) {
            return "UNKNOWN"; // an indicator that throws must not break the overview
        }
    }

    private static final String PARTNER_SELECT = """
            SELECT p.id, p.slug, p.display_name, p.amount_cap, p.active,
                   (SELECT count(*) FROM products x WHERE x.partner_id = p.id AND x.active) AS active_products,
                   (SELECT count(*) FROM products x WHERE x.partner_id = p.id) AS products,
                   (SELECT count(*) FROM seller_accounts s WHERE s.partner_id = p.id) AS sellers
            FROM partners p""";

    @Transactional(readOnly = true)
    public List<AdminDtos.Partner> partners() {
        return jdbc.query(PARTNER_SELECT + " ORDER BY p.display_name", (rs, n) -> partner(rs));
    }

    private static AdminDtos.Partner partner(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AdminDtos.Partner(rs.getLong("id"), rs.getString("slug"), rs.getString("display_name"), rs.getBigDecimal("amount_cap"),
                rs.getBoolean("active"), rs.getLong("active_products"), rs.getLong("products"), rs.getLong("sellers"));
    }

    /** Lowering the cap does not change existing loans; offers above the new cap fail at acceptance (TERMS_CHANGED). */
    @Transactional
    public AdminDtos.Partner updatePartner(AuthenticatedUser admin, long id, AdminDtos.UpdatePartner body) {
        var before = jdbc.query(PARTNER_SELECT + " WHERE p.id = ? FOR UPDATE OF p", (rs, n) -> partner(rs), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Partner"));
        String name = body.displayName().trim();
        if (name.isEmpty()) throw ApiException.badRequest("INVALID_NAME", "Enter a display name.");
        BigDecimal cap = body.amountCap().setScale(2, RoundingMode.UNNECESSARY);
        jdbc.update("UPDATE partners SET display_name = ?, amount_cap = ?, active = ? WHERE id = ?", name, cap, body.active(), id);
        var changes = new LinkedHashMap<String, Object>();
        if (!before.displayName().equals(name)) changes.put("displayName", Map.of("from", before.displayName(), "to", name));
        if (before.amountCap().compareTo(cap) != 0) changes.put("amountCap", Map.of("from", before.amountCap().toPlainString(), "to", cap.toPlainString()));
        if (before.active() != body.active()) changes.put("active", Map.of("from", before.active(), "to", body.active()));
        audit.record(admin, "ADMIN_PARTNER_UPDATED", "PARTNER", id, changes);
        return jdbc.query(PARTNER_SELECT + " WHERE p.id = ?", (rs, n) -> partner(rs), id).getFirst();
    }

    @Transactional(readOnly = true)
    public AdminDtos.Page<AdminDtos.AuditEvent> audit(String actor, String action, LocalDate from, LocalDate to, int page, int size) {
        Paging.check(page, size);
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        List<Object> args = new ArrayList<>();
        if (actor != null && !actor.isBlank()) { where.append(" AND strpos(u.email, lower(?)) > 0"); args.add(actor.trim()); }
        if (action != null && !action.isBlank()) { where.append(" AND e.action = ?"); args.add(action.trim()); }
        if (from != null) { where.append(" AND (e.created_at AT TIME ZONE 'Europe/Budapest')::date >= ?"); args.add(from); }
        if (to != null) { where.append(" AND (e.created_at AT TIME ZONE 'Europe/Budapest')::date <= ?"); args.add(to); }
        String base = " FROM audit_events e LEFT JOIN users u ON u.id = e.actor_user_id" + where;
        long total = jdbc.queryForObject("SELECT count(*)" + base, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var items = jdbc.query("""
                SELECT e.id, e.actor_user_id, u.email, e.actor_role, e.action, e.target_type, e.target_id, e.details::text AS details, e.created_at"""
                + base + " ORDER BY e.created_at DESC, e.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new AdminDtos.AuditEvent(rs.getLong("id"), rs.getObject("actor_user_id", Long.class), rs.getString("email"),
                        rs.getString("actor_role"), rs.getString("action"), rs.getString("target_type"), rs.getString("target_id"),
                        read(rs.getString("details")), rs.getObject("created_at", OffsetDateTime.class)),
                args.toArray());
        return new AdminDtos.Page<>(items, page, size, total);
    }

    private Map<String, Object> read(String json) {
        try { return mapper.readValue(json, MAP); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
