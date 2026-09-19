package com.rocketcredit.backend.applications;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ApplicationDtos {
    private ApplicationDtos() {}

    /**
     * One request shape for both entry points. Direct: partnerSlug + requestedAmount.
     * From a store page: partnerSlug + productId (the price is resolved server-side; a
     * requestedAmount, if also sent, must match it).
     */
    public record SubmitRequest(
            @NotBlank String partnerSlug,
            @Positive @Digits(integer = 10, fraction = 2) BigDecimal requestedAmount,
            Long productId,
            boolean useAi
    ) {}

    public record ApplicationDto(
            Long id,
            String partnerSlug,
            String partnerName,
            Long productId,
            String productName,
            BigDecimal requestedAmount,
            String currency,
            String decisionStatus,
            BigDecimal score,
            BigDecimal possibleAmount,
            List<String> reasons,
            Map<String, Object> factors,
            boolean aiRequested,
            String aiStatus,
            String policyVersion,
            String modelVersion,
            String preparationMode,
            OffsetDateTime observedAt,
            OffsetDateTime createdAt,
            /** present on the detail view only */
            Map<String, Object> featureSnapshot
    ) {}
}
