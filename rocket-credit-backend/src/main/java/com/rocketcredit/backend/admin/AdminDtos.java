// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.admin;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * What an administrator reads and sends. Account records have no password field. Review records carry the
 * decision, its reasons and factor scores, but not the customer's financial inputs, address, digital
 * footprint report, wallet or consents.
 */
public final class AdminDtos {
    private AdminDtos() {}

    public record Page<T>(List<T> items, int page, int size, long total) {}

    // ---- accounts --------------------------------------------------------------------------------

    public record User(long id, String email, String displayName, String role, boolean enabled,
                       OffsetDateTime createdAt, String partnerSlug, String partnerName) {}

    public record CreateSeller(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 120) String displayName,
            @NotNull Long partnerId,
            // BCrypt only uses the first 72 bytes, as in registration.
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
        @Override public String toString() { return "CreateSeller{partnerId=" + partnerId + "}"; }
    }

    public record ResetPassword(@NotBlank @Size(min = 8, max = 72) String password) {
        @Override public String toString() { return "ResetPassword{<redacted>}"; }
    }

    // ---- human review ----------------------------------------------------------------------------

    public record ReviewItem(long applicationId, long customerId, String partnerName, String productName,
                             BigDecimal requestedAmount, BigDecimal possibleAmount, BigDecimal score,
                             OffsetDateTime createdAt, String outcome, BigDecimal approvedAmount, OffsetDateTime decidedAt) {}

    /** The customer's chosen repayment term, from the quote the application was made with. */
    public record Plan(String termUnit, int installmentCount, String annualRate, String totalRepayment) {}

    public record Decision(String outcome, BigDecimal approvedAmount, String note, String reviewerEmail, OffsetDateTime decidedAt) {}

    public record ReviewDetail(long applicationId, long customerId, String partnerName, String productName,
                               BigDecimal requestedAmount, String currency, String decisionStatus, BigDecimal score,
                               BigDecimal possibleAmount, List<String> reasons, Map<String, Object> factors,
                               String aiStatus, String policyVersion, String modelVersion, OffsetDateTime createdAt,
                               Plan plan, Decision decision, boolean accepted) {}

    public record DecideReview(
            @NotBlank String outcome,
            @Positive @Digits(integer = 10, fraction = 2) BigDecimal approvedAmount,
            @NotBlank @Size(min = 10, max = 500) String note
    ) {}

    // ---- partners, overview, audit ---------------------------------------------------------------

    public record Partner(long id, String slug, String displayName, BigDecimal amountCap, boolean active,
                          long activeProducts, long products, long sellers) {}

    public record UpdatePartner(
            @NotBlank @Size(max = 120) String displayName,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amountCap,
            @NotNull Boolean active
    ) {}

    public record Overview(Map<String, Map<String, Long>> usersByRole, Map<String, Long> applicationsByDecision,
                           Map<String, Long> reviews, long activeLoans, long overdueInstallments,
                           Map<String, String> services) {}

    public record AuditEvent(long id, Long actorUserId, String actorEmail, String actorRole, String action,
                             String targetType, String targetId, Map<String, Object> details, OffsetDateTime createdAt) {}
}
