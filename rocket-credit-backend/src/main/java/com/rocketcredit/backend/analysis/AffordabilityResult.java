package com.rocketcredit.backend.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Mirror of the analysis service's affordability response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AffordabilityResult(
        String formulaVersion,
        String policyVersion,
        String currency,
        int termMonths,
        BigDecimal baseAmount,
        BigDecimal partnerAmount,
        BigDecimal monthlyPaymentCapacity,
        Map<String, BigDecimal> breakdown,
        List<String> reasons
) {}
