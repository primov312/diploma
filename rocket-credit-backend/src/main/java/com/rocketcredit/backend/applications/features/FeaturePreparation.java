package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.analysis.FeatureBundle;
import com.rocketcredit.backend.applications.CreditApplicationEntity;
import java.math.BigDecimal;

/** Assembles the three feature sections into the bundle sent to analysis. */
public interface FeaturePreparation {

    /** The three sections, before amount/cap/AI flags are attached. */
    record Sections(FeatureBundle.Profile profile, FeatureBundle.History history, FeatureBundle.Finance finance,
                    com.rocketcredit.backend.analysis.AffordabilityRequest.Inputs affordability) {
        Sections(FeatureBundle.Profile profile, FeatureBundle.History history, FeatureBundle.Finance finance) {
            this(profile, history, finance, null);
        }
    }

    Sections prepare(FeatureProviders.Context ctx);

    CreditApplicationEntity.PreparationMode mode();

    static FeatureBundle toBundle(Sections s, BigDecimal requestedAmount, BigDecimal partnerCap, boolean useAi,
                                  FeatureProviders.Context ctx) {
        var affordability = s.affordability();
        if (affordability != null) {
            affordability = new com.rocketcredit.backend.analysis.AffordabilityRequest.Inputs(
                    affordability.monthlyNetIncome(), affordability.housingSituation(), affordability.expenseMode(),
                    affordability.housingCost(), affordability.groceriesCost(), affordability.utilitiesCost(),
                    affordability.transportCost(), affordability.otherLivingCosts(), affordability.legacyLivingExpenses(),
                    affordability.monthlyObligations(), affordability.districtRentReference(),
                    affordability.districtGroceryReference(), affordability.referencesEligible(), partnerCap,
                    affordability.financialRevision(), affordability.generation());
        }
        return new FeatureBundle(requestedAmount, "USD", partnerCap, useAi, ctx.observedAt(),
                s.profile(), s.history(), s.finance(), affordability);
    }
}
