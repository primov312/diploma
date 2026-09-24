package com.rocketcredit.backend.users;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SaveFinancialInputsRequest(
        @NotNull @Min(1) Integer expectedRevision,
        @Digits(integer = 10, fraction = 2) BigDecimal monthlyNetIncome,
        @NotNull HousingSituation housingSituation,
        @NotNull ExpenseMode expenseMode,
        @Digits(integer = 10, fraction = 2) BigDecimal housingCost,
        @Digits(integer = 10, fraction = 2) BigDecimal groceriesCost,
        @Digits(integer = 10, fraction = 2) BigDecimal utilitiesCost,
        @Digits(integer = 10, fraction = 2) BigDecimal transportCost,
        @Digits(integer = 10, fraction = 2) BigDecimal otherLivingCosts,
        @Digits(integer = 10, fraction = 2) BigDecimal legacyLivingExpenses,
        @NotNull @Digits(integer = 10, fraction = 2) BigDecimal monthlyObligations
) {
    public enum HousingSituation { RENTING, OWNER, FAMILY, OTHER }
    public enum ExpenseMode { AGGREGATE, ITEMIZED }
}
