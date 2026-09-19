package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.analysis.FeatureBundle;
import com.rocketcredit.backend.applications.CreditApplicationEntity;
import java.math.BigDecimal;

/** Assembles the three feature sections into the bundle sent to analysis. */
public interface FeaturePreparation {

    /** The three sections, before amount/cap/AI flags are attached. */
    record Sections(FeatureBundle.Profile profile, FeatureBundle.History history, FeatureBundle.Finance finance) {}

    Sections prepare(FeatureProviders.Context ctx);

    CreditApplicationEntity.PreparationMode mode();

    static FeatureBundle toBundle(Sections s, BigDecimal requestedAmount, BigDecimal partnerCap, boolean useAi,
                                  FeatureProviders.Context ctx) {
        return new FeatureBundle(requestedAmount, "USD", partnerCap, useAi, ctx.observedAt(),
                s.profile(), s.history(), s.finance());
    }
}
