package com.rocketcredit.backend.fixtures;

import com.rocketcredit.backend.partners.PartnerEntity;
import com.rocketcredit.backend.partners.PartnerRepository;
import com.rocketcredit.backend.transactions.TransactionEntity;
import com.rocketcredit.backend.transactions.TransactionRepository;
import com.rocketcredit.backend.users.DemoFinancialProfileEntity;
import com.rocketcredit.backend.users.DemoFinancialProfileRepository;
import com.rocketcredit.backend.users.FinancialInputsService;
import com.rocketcredit.backend.users.UserEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every new registration receives a small, clearly labelled synthetic dataset so the
 * dashboard and scoring have something to show. It is generated for that user only and
 * never copied from another account. Values are modest: enough for a small approval,
 * not enough to look like a seeded persona.
 */
@Service
public class StarterDataService {
    public static final String STARTER_PREFIX = "starter-";
    static final String DESCRIPTION = "[Synthetic starter data] sample purchase";

    private static final BigDecimal INCOME = new BigDecimal("3000.00");
    private static final BigDecimal EXPENSES = new BigDecimal("2200.00");
    private static final BigDecimal OBLIGATIONS = new BigDecimal("200.00");

    private final PartnerRepository partners;
    private final TransactionRepository transactions;
    private final DemoFinancialProfileRepository profiles;
    private final FinancialInputsService financialInputs;

    public StarterDataService(PartnerRepository partners, TransactionRepository transactions,
                              DemoFinancialProfileRepository profiles, FinancialInputsService financialInputs) {
        this.partners = partners;
        this.transactions = transactions;
        this.profiles = profiles;
        this.financialInputs = financialInputs;
    }

    @Transactional
    public void createFor(UserEntity user) {
        profiles.saveAndFlush(new DemoFinancialProfileEntity(user.getId(), INCOME, EXPENSES, OBLIGATIONS,
                false, false, DemoFinancialProfileEntity.Source.STARTER));
        financialInputs.current(user.getId());

        LocalDate today = LocalDate.now();
        List<PartnerEntity> all = partners.findAll();
        for (int i = 0; i < all.size(); i++) {
            PartnerEntity p = all.get(i);
            String fixtureId = STARTER_PREFIX + user.getId() + "-" + p.getSlug();
            if (transactions.existsByFixtureId(fixtureId)) continue;
            transactions.save(new TransactionEntity(user.getId(), p.getId(), fixtureId,
                    new BigDecimal("39.00").add(BigDecimal.valueOf(10L * i)),
                    today.minusDays(20L + 25L * i), TransactionEntity.Status.COMPLETED, true, DESCRIPTION));
        }
    }
}
