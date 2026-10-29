// AI-assisted (Claude), 2026: also seeds the demo administrator, sellers and financed orders; reviewed by the author.
package com.rocketcredit.backend.fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.affordability.AffordabilityDtos;
import com.rocketcredit.backend.consents.ConsentService;
import com.rocketcredit.backend.partners.PartnerEntity;
import com.rocketcredit.backend.partners.PartnerRepository;
import com.rocketcredit.backend.transactions.TransactionEntity;
import com.rocketcredit.backend.transactions.TransactionRepository;
import com.rocketcredit.backend.users.DemoFinancialProfileEntity;
import com.rocketcredit.backend.users.DemoFinancialProfileRepository;
import com.rocketcredit.backend.users.FinancialInputsService;
import com.rocketcredit.backend.users.UserEntity;
import com.rocketcredit.backend.users.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the demo customers at startup. Safe to run on every start: users are matched by
 * email, profiles by user, transactions by their stable fixture ID, so a second run
 * inserts nothing new and the history never inflates. Passwords are hashed here with the
 * same encoder as registration; no hash is stored in the fixture file. {@link StaffDemoData} adds the demo
 * administrator and sellers (same password) and a few financed orders for the seller screens.
 */
@Component
public class DemoDataLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    static final String FIXTURE_PATH = "fixtures/demo-customers.json";
    static final String HISTORY_VERSION = "demo-history-v1";
    private static final BigDecimal PAYMENTS = BigDecimal.valueOf(6);

    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository users;
    private final DemoFinancialProfileRepository profiles;
    private final FinancialInputsService financialInputs;
    private final PartnerRepository partners;
    private final TransactionRepository transactions;
    private final JdbcTemplate jdbc;
    private final ConsentService consents;
    private final StaffDemoData staff;

    public DemoDataLoader(ObjectMapper objectMapper, PasswordEncoder passwordEncoder, UserRepository users,
                          DemoFinancialProfileRepository profiles, FinancialInputsService financialInputs, PartnerRepository partners,
                          TransactionRepository transactions, JdbcTemplate jdbc, ConsentService consents, StaffDemoData staff) {
        this.staff = staff;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
        this.profiles = profiles;
        this.financialInputs = financialInputs;
        this.partners = partners;
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.consents = consents;
    }

    @Override
    public void run(ApplicationArguments args) {
        var result = load();
        log.info("demo fixtures: {} customers checked, {} users created, {} transactions inserted, {} history months inserted, {} staff/order accounts created",
                result.customers(), result.usersCreated(), result.transactionsInserted(), result.historyMonthsInserted(), result.staffCreated());
    }

    public record LoadResult(int customers, int usersCreated, int transactionsInserted, int historyMonthsInserted, int staffCreated) {}

    @Transactional
    public LoadResult load() {
        DemoFixtures fixtures = read();
        Map<String, PartnerEntity> partnerBySlug = partners.findAll().stream()
                .collect(Collectors.toMap(PartnerEntity::getSlug, Function.identity()));
        String passwordHash = passwordEncoder.encode(fixtures.password());

        int created = 0;
        int inserted = 0;
        int historyMonths = 0;
        for (DemoFixtures.Customer c : fixtures.customers()) {
            UserEntity user = users.findByEmail(c.email()).orElse(null);
            if (user == null) {
                OffsetDateTime registeredAt = fixtures.anchorDate().minusMonths(c.accountAgeMonths())
                        .atStartOfDay().atOffset(ZoneOffset.UTC);
                user = users.save(new UserEntity(c.email(), passwordHash, c.displayName(), registeredAt));
                created++;
            }

            var f = c.finance();
            profiles.saveAndFlush(new DemoFinancialProfileEntity(user.getId(), f.monthlyIncome(), f.monthlyExpenses(),
                    f.monthlyObligations(), f.profileComplete(), f.emailVerified(),
                    DemoFinancialProfileEntity.Source.FIXTURE));
            financialInputs.current(user.getId());
            // Fixture accounts stand in for customers who already accepted the consent screen; the record says DEMO_FIXTURE.
            consents.grantAllForDemoFixture(user.getId());
            historyMonths += seedAffordabilityHistory(c, user.getId(), partnerBySlug.values());

            for (DemoFixtures.History h : c.history()) {
                PartnerEntity partner = partnerBySlug.get(h.partner());
                if (partner == null) throw new IllegalStateException("fixture references unknown partner " + h.partner());
                for (TransactionEntity t : generate(c, h, partner, fixtures.anchorDate(), user.getId())) {
                    if (!transactions.existsByFixtureId(t.getFixtureId())) {
                        transactions.save(t);
                        inserted++;
                    }
                }
            }
        }
        int staffCreated = staff.load(passwordHash);
        return new LoadResult(fixtures.customers().size(), created, inserted, historyMonths, staffCreated);
    }

    /**
     * Stores the fixture's monthly amounts as SYNTHETIC snapshots so the "What you can afford" chart has a
     * history. Months count back from the current UTC month, which real calculations fill. Each month gets a
     * stable generation (-yyyymm), so reruns hit the unique key, and a month that already has any snapshot is
     * left alone, so real estimates are never hidden.
     */
    private int seedAffordabilityHistory(DemoFixtures.Customer c, long userId, Collection<PartnerEntity> sellers) {
        List<BigDecimal> amounts = c.affordabilityHistory() == null ? List.of() : c.affordabilityHistory();
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        int inserted = 0;
        for (int i = 0; i < amounts.size(); i++) {
            YearMonth month = current.minusMonths(amounts.size() - i);
            BigDecimal amount = amounts.get(i).setScale(2, RoundingMode.HALF_UP);
            OffsetDateTime start = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            var limits = sellers.stream().map(p -> new AffordabilityDtos.PartnerAmount(
                    p.getSlug(), p.getAmountCap(), amount.min(p.getAmountCap()))).toList();
            inserted += jdbc.update("""
                    INSERT INTO affordability_snapshots (user_id, generation, financial_revision, formula_version,
                        policy_version, calculated_at, base_amount, monthly_payment_capacity, breakdown, partners, reasons, data_source)
                    SELECT ?, ?, (SELECT min(revision) FROM financial_input_revisions WHERE user_id = ?), ?,
                        ?, ?, ?, ?, '{}'::jsonb, ?::jsonb, '[]'::jsonb, 'SYNTHETIC'
                    WHERE NOT EXISTS (SELECT 1 FROM affordability_snapshots
                                      WHERE user_id = ? AND calculated_at >= ? AND calculated_at < ?)
                    ON CONFLICT (user_id, generation, formula_version) DO NOTHING
                    """, userId, -(month.getYear() * 100L + month.getMonthValue()), userId, HISTORY_VERSION,
                    HISTORY_VERSION, start.plusDays(14).plusHours(12), amount,
                    amount.divide(PAYMENTS, 2, RoundingMode.HALF_UP), json(limits), userId, start, start.plusMonths(1));
        }
        return inserted;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Deterministic sequence: index 0 is the newest purchase, on the anchor date. */
    static List<TransactionEntity> generate(DemoFixtures.Customer c, DemoFixtures.History h,
                                            PartnerEntity partner, LocalDate anchor, Long userId) {
        List<TransactionEntity> out = new ArrayList<>();
        for (int i = 0; i < h.count(); i++) {
            String fixtureId = "%s-%s-%02d".formatted(c.fixtureId(), h.partner(), i);
            var amount = h.amounts().get(i % h.amounts().size());
            var date = anchor.minusDays((long) i * h.everyDays());
            var status = h.refunded().contains(i) ? TransactionEntity.Status.REFUNDED : TransactionEntity.Status.COMPLETED;
            boolean onTime = !h.late().contains(i);
            out.add(new TransactionEntity(userId, partner.getId(), fixtureId, amount, date, status, onTime,
                    "Demo purchase at " + partner.getDisplayName()));
        }
        return out;
    }

    private DemoFixtures read() {
        try (var in = new ClassPathResource(FIXTURE_PATH).getInputStream()) {
            return objectMapper.readValue(in, DemoFixtures.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + FIXTURE_PATH, e);
        }
    }
}
