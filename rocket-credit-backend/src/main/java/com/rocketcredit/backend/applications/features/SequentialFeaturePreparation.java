package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.applications.CreditApplicationEntity;
import org.springframework.stereotype.Component;

/** Baseline: profile -> history -> finance, one after another on the request thread. */
@Component
public class SequentialFeaturePreparation implements FeaturePreparation {
    private final FeatureProviders.ProfileFeatureProvider profile;
    private final FeatureProviders.HistoryFeatureProvider history;
    private final FeatureProviders.FinanceFeatureProvider finance;

    public SequentialFeaturePreparation(FeatureProviders.ProfileFeatureProvider profile,
                                        FeatureProviders.HistoryFeatureProvider history,
                                        FeatureProviders.FinanceFeatureProvider finance) {
        this.profile = profile;
        this.history = history;
        this.finance = finance;
    }

    @Override
    public Sections prepare(FeatureProviders.Context ctx) {
        var preparedFinance = finance.financeBundle(ctx);
        return new Sections(profile.profile(ctx), history.history(ctx), preparedFinance.finance(),
                preparedFinance.affordability());
    }

    @Override
    public CreditApplicationEntity.PreparationMode mode() {
        return CreditApplicationEntity.PreparationMode.SEQUENTIAL;
    }
}
