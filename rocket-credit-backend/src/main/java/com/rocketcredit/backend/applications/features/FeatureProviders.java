package com.rocketcredit.backend.applications.features;

import com.rocketcredit.backend.analysis.AffordabilityRequest;
import com.rocketcredit.backend.analysis.FeatureBundle;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Three independent feature sources. Each takes immutable inputs and returns an immutable
 * record, so the parallel implementation (Step 7) can run them on separate threads without
 * sharing a persistence context or mutable state. A provider returns {@code null} when the
 * evidence is missing; the analysis service then answers REVIEW.
 */
public final class FeatureProviders {
    private FeatureProviders() {}

    /** What every provider needs; captured once per application. */
    public record Context(Long userId, Long partnerId, OffsetDateTime observedAt) {}

    public interface ProfileFeatureProvider {
        FeatureBundle.Profile profile(Context ctx);
    }

    public interface HistoryFeatureProvider {
        FeatureBundle.History history(Context ctx);
    }

    public interface FinanceFeatureProvider {
        FeatureBundle.Finance finance(Context ctx);

        default FinanceFeatures financeBundle(Context ctx) {
            return new FinanceFeatures(finance(ctx), null);
        }
    }

    public record FinanceFeatures(FeatureBundle.Finance finance, AffordabilityRequest.Inputs affordability) {}
}
