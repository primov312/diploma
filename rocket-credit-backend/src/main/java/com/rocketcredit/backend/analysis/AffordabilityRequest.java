package com.rocketcredit.backend.analysis;

import java.math.BigDecimal;

/** Versioned input contract for the pure affordability calculation endpoint. */
public record AffordabilityRequest(Inputs inputs) {
    public record Inputs(
            BigDecimal monthlyNetIncome,
            String housingSituation,
            String expenseMode,
            BigDecimal housingCost,
            BigDecimal groceriesCost,
            BigDecimal utilitiesCost,
            BigDecimal transportCost,
            BigDecimal otherLivingCosts,
            BigDecimal legacyLivingExpenses,
            BigDecimal monthlyObligations,
            BigDecimal districtRentReference,
            BigDecimal districtGroceryReference,
            boolean referencesEligible,
            BigDecimal partnerCap,
            Integer financialRevision,
            Long generation
    ) {}
}
