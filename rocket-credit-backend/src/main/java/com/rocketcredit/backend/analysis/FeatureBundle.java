package com.rocketcredit.backend.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * What the backend sends to Python: derived features only. No user ID, name,
 * email or session data. A null section means "evidence missing" and makes the
 * analysis return REVIEW.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeatureBundle(
        BigDecimal requestedAmount,
        String currency,
        BigDecimal partnerCap,
        boolean useAi,
        OffsetDateTime observedAt,
        Profile profile,
        History history,
        Finance finance,
        AffordabilityRequest.Inputs affordability
) {
    public FeatureBundle(BigDecimal requestedAmount, String currency, BigDecimal partnerCap, boolean useAi,
                        OffsetDateTime observedAt, Profile profile, History history, Finance finance) {
        this(requestedAmount, currency, partnerCap, useAi, observedAt, profile, history, finance, null);
    }
    public record Profile(int accountAgeMonths, boolean profileComplete, boolean emailVerified) {}

    public record History(int partnerOrders12m, BigDecimal partnerAvgOrderValue, double partnerRefundRate,
                          double partnerOnTimeRatio, int partnerTenureMonths, int totalOrders12m) {}

    public record Finance(BigDecimal monthlyIncome, BigDecimal monthlyExpenses, BigDecimal monthlyObligations) {}
}
