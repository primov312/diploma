package com.rocketcredit.backend.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Mirror of the Python ScoreResponse model. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisDecision(
        String decisionStatus,
        BigDecimal score,
        BigDecimal possibleAmount,
        String currency,
        List<String> reasons,
        Map<String, Factor> factors,
        String policyVersion,
        boolean aiRequested,
        String aiStatus,
        String modelVersion,
        Map<String, Double> aiContributions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Factor(Double score, double weight, List<String> reasons, Map<String, Object> details) {}
}
