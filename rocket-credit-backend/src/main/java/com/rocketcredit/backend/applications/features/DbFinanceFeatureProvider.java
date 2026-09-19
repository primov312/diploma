package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.analysis.FeatureBundle;
import com.rocketcredit.backend.users.DemoFinancialProfileRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DbFinanceFeatureProvider implements FeatureProviders.FinanceFeatureProvider {
    private final DemoFinancialProfileRepository profiles;

    public DbFinanceFeatureProvider(DemoFinancialProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public FeatureBundle.Finance finance(FeatureProviders.Context ctx) {
        return profiles.findById(ctx.userId())
                .map(p -> new FeatureBundle.Finance(p.getMonthlyIncome(), p.getMonthlyExpenses(), p.getMonthlyObligations()))
                .orElse(null);
    }
}
