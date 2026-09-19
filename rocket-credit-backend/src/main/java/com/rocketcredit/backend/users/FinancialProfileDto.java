package com.rocketcredit.backend.users;

import java.math.BigDecimal;

/** Read-only view of the synthetic profile. There is no endpoint that writes these values. */
public record FinancialProfileDto(BigDecimal monthlyIncome, BigDecimal monthlyExpenses, BigDecimal monthlyObligations,
                                  boolean profileComplete, boolean emailVerified, String syntheticSource,
                                  int accountAgeMonths) {}
