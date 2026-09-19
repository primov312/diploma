package com.rocketcredit.backend.analysis;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Shows up as {@code components.analysis} in GET /actuator/health. */
@Component("analysis")
public class AnalysisHealthIndicator implements HealthIndicator {
    private final AnalysisClient client;

    public AnalysisHealthIndicator(AnalysisClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            var h = client.health();
            return Health.up()
                    .withDetail("policyVersion", h.policyVersion())
                    .withDetail("modelLoaded", h.modelLoaded())
                    .withDetail("modelVersion", h.modelVersion() == null ? "none" : h.modelVersion())
                    .build();
        } catch (AnalysisUnavailableException e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
