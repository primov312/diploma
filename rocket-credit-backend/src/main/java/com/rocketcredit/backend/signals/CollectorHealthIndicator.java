// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.signals;

import com.rocketcredit.backend.common.ApiException;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Shows up as {@code components.collector} in GET /actuator/health and in the administrator overview.
 * Calls only the collector's public {@code /health}; the allow-list it returns is not repeated here.
 */
@Component("collector")
public class CollectorHealthIndicator implements HealthIndicator {
    private final CollectorClient client;

    public CollectorHealthIndicator(CollectorClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            String format = client.health();
            return Health.up().withDetail("format", format == null ? "unknown" : format).build();
        } catch (ApiException e) {
            return Health.down().withDetail("error", e.getCode()).build();
        }
    }
}
