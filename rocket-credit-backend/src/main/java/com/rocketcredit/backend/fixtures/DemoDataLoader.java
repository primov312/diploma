package com.rocketcredit.backend.fixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.partners.PartnerEntity;
import com.rocketcredit.backend.partners.PartnerRepository;
import com.rocketcredit.backend.transactions.TransactionEntity;
import com.rocketcredit.backend.transactions.TransactionRepository;
import com.rocketcredit.backend.users.DemoFinancialProfileEntity;
import com.rocketcredit.backend.users.DemoFinancialProfileRepository;
import com.rocketcredit.backend.users.UserEntity;
import com.rocketcredit.backend.users.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the demo customers at startup. Safe to run on every start: users are matched by
 * email, profiles by user, transactions by their stable fixture ID, so a second run
 * inserts nothing new and the history never inflates. Passwords are hashed here with the
 * same encoder as registration; no hash is stored in the fixture file.
 */
@Component
public class DemoDataLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    static final String FIXTURE_PATH = "fixtures/demo-customers.json";

    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository users;
    private final DemoFinancialProfileRepository profiles;
    private final PartnerRepository partners;
    private final TransactionRepository transactions;

    public DemoDataLoader(ObjectMapper objectMapper, PasswordEncoder passwordEncoder, UserRepository users,
                          DemoFinancialProfileRepository profiles, PartnerRepository partners,
                          TransactionRepository transactions) {
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
        this.profiles = profiles;
        this.partners = partners;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        var result = load();
        log.info("demo fixtures: {} customers checked, {} users created, {} transactions inserted",
                result.customers(), result.usersCreated(), result.transactionsInserted());
    }

    public record LoadResult(int customers, int usersCreated, int transactionsInserted) {}

    @Transactional
    public LoadResult load() {
        DemoFixtures fixtures = read();
        Map<String, PartnerEntity> partnerBySlug = partners.findAll().stream()
                .collect(Collectors.toMap(PartnerEntity::getSlug, Function.identity()));
        String passwordHash = passwordEncoder.encode(fixtures.password());

        int created = 0;
        int inserted = 0;
        for (DemoFixtures.Customer c : fixtures.customers()) {
            UserEntity user = users.findByEmail(c.email()).orElse(null);
            if (user == null) {
                OffsetDateTime registeredAt = fixtures.anchorDate().minusMonths(c.accountAgeMonths())
                        .atStartOfDay().atOffset(ZoneOffset.UTC);
                user = users.save(new UserEntity(c.email(), passwordHash, c.displayName(), registeredAt));
                created++;
            }

            var f = c.finance();
            profiles.save(new DemoFinancialProfileEntity(user.getId(), f.monthlyIncome(), f.monthlyExpenses(),
                    f.monthlyObligations(), f.profileComplete(), f.emailVerified(),
                    DemoFinancialProfileEntity.Source.FIXTURE));

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
        return new LoadResult(fixtures.customers().size(), created, inserted);
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
