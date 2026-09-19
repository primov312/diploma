package com.rocketcredit.backend.analysis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Location and credentials of the stateless Python analysis service. */
@ConfigurationProperties(prefix = "rocket.analysis")
public record AnalysisProperties(
        String baseUrl,
        String sharedSecret,
        Duration connectTimeout,
        Duration readTimeout
) {
    public AnalysisProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:8000";
        if (sharedSecret == null) sharedSecret = "";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(2);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(5);
    }
}
