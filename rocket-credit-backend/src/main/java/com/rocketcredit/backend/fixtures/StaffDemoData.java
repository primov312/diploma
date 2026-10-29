// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.auth.RegistrationService;
import com.rocketcredit.backend.payments.RepaymentCalculator;
import com.rocketcredit.backend.users.Role;
import com.rocketcredit.backend.users.UserEntity;
import com.rocketcredit.backend.users.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo administrator, one seller per demo partner, and a few financed orders so the seller screens are not
 * empty. Called from {@link DemoDataLoader} inside its transaction; idempotent (accounts are matched by e-mail,
 * the orders are created once together with their own synthetic customer).
 *
 * <p>The orders belong to a separate synthetic customer, not to the four personas: a loan adds monthly
 * obligations and a purchase adds history, which would change the personas' decisions. An existing account
 * with a reserved e-mail but another role is never promoted; it is left alone and logged.
 * Dates are taken from the system clock, not from the Clock bean, because tests replace that bean.
 */
@Component
public class StaffDemoData {
    private static final Logger log = LoggerFactory.getLogger(StaffDemoData.class);
    static final String ADMIN_EMAIL = "admin@demo.rocket.local";
    static final String ORDERS_EMAIL = "orders@demo.rocket.local";
    /** seller e-mail, display name, partner slug */
    static final List<String[]> SELLERS = List.of(
            new String[] {"netflix@demo.rocket.local", "Netflix Seller (demo)", "streambox"},
            new String[] {"zara@demo.rocket.local", "Zara Seller (demo)", "threadly"},
            new String[] {"amazon@demo.rocket.local", "Amazon Seller (demo)", "markethub"});

    /** product fixture id, days ago, term unit, instalments, settled early (closed) */
    private record Order(String product, int daysAgo, String unit, int count, boolean closed) {}
    private static final List<Order> ORDERS = List.of(
            new Order("streambox-premium-annual", 3, "MONTH", 3, false),
            new Order("streambox-family-annual", 11, "MONTH", 6, false),
            new Order("streambox-cinema-year", 19, "MONTH", 3, true),
            new Order("streambox-series-season", 26, "MONTH", 1, false),
            new Order("threadly-running-shoes", 2, "MONTH", 3, false),
            new Order("threadly-wool-coat", 5, "MONTH", 3, false),
            new Order("threadly-leather-boots", 13, "MONTH", 6, false),
            new Order("threadly-denim-jacket", 21, "MONTH", 2, true),
            new Order("markethub-espresso", 4, "MONTH", 12, false),
            new Order("markethub-standing-desk", 9, "MONTH", 12, false),
            new Order("markethub-monitor-27", 16, "MONTH", 6, true),
            new Order("markethub-earbuds", 24, "MONTH", 3, false));

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final RegistrationService registration;
    private final RepaymentCalculator pricing;
    private final ObjectMapper mapper;

    public StaffDemoData(UserRepository users, JdbcTemplate jdbc, RegistrationService registration,
                  RepaymentCalculator pricing, ObjectMapper mapper) {
        this.users = users;
        this.jdbc = jdbc;
        this.registration = registration;
        this.pricing = pricing;
        this.mapper = mapper;
    }

    /**
     * One transaction, also when DemoDataLoader.run() calls load() without its own proxy: a seller and its
     * partner link must be committed together (the database checks the pair at commit).
     *
     * @return number of accounts created
     */
    @Transactional
    public int load(String passwordHash) {
        int created = 0;
        if (account(ADMIN_EMAIL, "Demo Administrator", Role.ADMIN, passwordHash) != null) created++;
        for (String[] s : SELLERS) {
            Long partnerId = jdbc.query("SELECT id FROM partners WHERE slug = ?", (rs, n) -> rs.getLong(1), s[2])
                    .stream().findFirst().orElseThrow(() -> new IllegalStateException("unknown partner " + s[2]));
            UserEntity seller = account(s[0], s[1], Role.SELLER, passwordHash);
            if (seller != null) {
                jdbc.update("INSERT INTO seller_accounts(user_id, partner_id) VALUES (?, ?)", seller.getId(), partnerId);
                created++;
            }
        }
        if (users.findByEmail(ORDERS_EMAIL).isEmpty()) {
            seedOrders(passwordHash);
            created++;
        }
        return created;
    }

    /** Creates the account, or returns null when the e-mail is already taken (whatever its role). */
    private UserEntity account(String email, String name, Role role, String passwordHash) {
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != role) log.warn("demo account {} exists with another role; not changed", role);
            return null;
        }
        return users.saveAndFlush(new UserEntity(email, passwordHash, name, role));
    }

    private void seedOrders(String passwordHash) {
        // A normal customer account (wallet, starter data); the demo password is set like the personas'.
        long user = registration.register(ORDERS_EMAIL, "unused-" + System.nanoTime(), "Synthetic Orders Demo").getId();
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, user);
        long generation = jdbc.queryForObject("SELECT generation FROM financial_input_state WHERE user_id = ?", Long.class, user);
        LocalDate today = LocalDate.now(RepaymentCalculator.ZONE);
        for (int i = 0; i < ORDERS.size(); i++) {
            Order o = ORDERS.get(i);
            var product = jdbc.queryForMap("SELECT id, partner_id, price, name FROM products WHERE fixture_id = ?", o.product());
            long partner = ((Number) product.get("partner_id")).longValue();
            long productId = ((Number) product.get("id")).longValue();
            BigDecimal price = (BigDecimal) product.get("price");
            LocalDate start = today.minusDays(o.daysAgo());
            OffsetDateTime at = start.atTime(11, 0).atZone(RepaymentCalculator.ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
            var schedule = pricing.calculate(price, o.unit(), o.count(), start);
            long quote = jdbc.queryForObject("""
                    INSERT INTO credit_quotes(user_id, partner_id, product_id, principal, term_unit, installment_count, annual_rate,
                        pricing_version, generation, start_date, expires_at, schedule, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) RETURNING id""", Long.class,
                    user, partner, productId, price, o.unit(), o.count(), pricing.rate(), pricing.version(), generation,
                    start, at.plusMinutes(15), json(schedule), at);
            long application = jdbc.queryForObject("""
                    INSERT INTO credit_applications(user_id, partner_id, product_id, requested_amount, decision_status, score,
                        possible_amount, reasons, factors, feature_snapshot, ai_requested, ai_status, policy_version, observed_at,
                        quote_id, created_at)
                    VALUES (?, ?, ?, ?, 'APPROVED', 1, ?, '["SYNTHETIC_SELLER_FIXTURE"]'::jsonb, '{}'::jsonb,
                        '{"source":"SYNTHETIC_SELLER_FIXTURE"}'::jsonb, false, 'NOT_REQUESTED', 'fixture-v1', ?, ?, ?) RETURNING id""",
                    Long.class, user, partner, productId, price, price, at, quote, at);
            long loan = jdbc.queryForObject("""
                    INSERT INTO loans(user_id, application_id, quote_id, partner_id, product_id, principal, term_unit, installment_count,
                        annual_rate, pricing_version, start_date, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                    user, application, quote, partner, productId, price, o.unit(), o.count(), pricing.rate(), pricing.version(), start, at);
            for (var part : schedule.installments()) {
                jdbc.update("INSERT INTO repayment_installments(loan_id, sequence, period_start, due_date, principal, interest) VALUES (?, ?, ?, ?, ?, ?)",
                        loan, part.sequence(), part.periodStart(), part.dueDate(), new BigDecimal(part.principal()), new BigDecimal(part.interest()));
            }
            jdbc.update("""
                    INSERT INTO transactions(user_id, partner_id, product_id, fixture_id, amount, occurred_on, status, paid_on_time,
                        description, origin, payment_method, loan_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED', true, ?, 'DEMO_PURCHASE', 'CREDIT', ?, ?)""",
                    user, partner, productId, "seller-orders-" + i, price, start, product.get("name"), loan, at);
            if (o.closed()) settleSameDay(user, loan, price, at, i);
        }
    }

    /**
     * Closed order: the customer adds the principal and settles on the first day, so no interest is earned
     * (the same rule as PaymentService.settle). Both wallet entries cancel out; the balance stays 0.
     */
    private void settleSameDay(long user, long loan, BigDecimal principal, OffsetDateTime at, int i) {
        long topUpOp = operation(user, "TOP_UP", "seller-orders-topup-" + i, at);
        jdbc.update("INSERT INTO wallet_entries(user_id, operation_id, kind, amount, balance_after, description, created_at) VALUES (?, ?, 'TOP_UP', ?, ?, 'Demo top-up', ?)",
                user, topUpOp, principal, principal, at);
        long settleOp = operation(user, "SETTLEMENT", "seller-orders-settle-" + i, at);
        long entry = jdbc.queryForObject("""
                INSERT INTO wallet_entries(user_id, operation_id, kind, amount, balance_after, loan_id, description, created_at)
                VALUES (?, ?, 'EARLY_SETTLEMENT', ?, 0, ?, ?, ?) RETURNING id""", Long.class,
                user, settleOp, principal.negate(), loan, "Early settlement of credit #" + loan, at.plusMinutes(1));
        jdbc.update("""
                UPDATE repayment_installments SET principal_paid = principal, interest_paid = 0, interest_waived = interest,
                    payment_entry_id = ?, paid_at = ? WHERE loan_id = ?""", entry, at.plusMinutes(1), loan);
        jdbc.update("UPDATE loans SET status = 'SETTLED_EARLY', version = version + 1, closed_at = ? WHERE id = ?", at.plusMinutes(1), loan);
    }

    private long operation(long user, String type, String key, OffsetDateTime at) {
        return jdbc.queryForObject("""
                INSERT INTO financial_operations(user_id, operation_type, idempotency_key, fingerprint, result, created_at)
                VALUES (?, ?, ?, '{"source":"SYNTHETIC_SELLER_FIXTURE"}', '{}'::jsonb, ?) RETURNING id""", Long.class, user, type, key, at);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
