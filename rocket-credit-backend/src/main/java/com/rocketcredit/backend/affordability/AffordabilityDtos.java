package com.rocketcredit.backend.affordability;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public final class AffordabilityDtos {
    private AffordabilityDtos() {}

    public record PartnerAmount(String slug, BigDecimal cap, BigDecimal possibleAmount) {}
    public record Estimate(String status, OffsetDateTime calculatedAt, long generation, int financialRevision,
                           String formulaVersion, String policyVersion, String currency, int termMonths,
                           BigDecimal baseAmount, BigDecimal monthlyPaymentCapacity,
                           Map<String, BigDecimal> breakdown, List<PartnerAmount> partners,
                           List<String> reasons, boolean stale) {}
    public record HistoryPoint(YearMonth month, OffsetDateTime calculatedAt, BigDecimal amount,
                               Map<String, BigDecimal> partnerAmounts, String formulaVersion,
                               String policyVersion, String dataSource) {}
    public record Job(long id, String state, int attemptCount, String failureCode,
                      OffsetDateTime createdAt, OffsetDateTime completedAt) {}
}
