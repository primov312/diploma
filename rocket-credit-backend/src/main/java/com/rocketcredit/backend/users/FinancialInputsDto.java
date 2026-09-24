package com.rocketcredit.backend.users;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FinancialInputsDto(
        int revision,
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
        String source,
        OffsetDateTime updatedAt
) {}
