// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.signals;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Location and credentials of the footprint collector service (the web scraping module). */
@ConfigurationProperties(prefix = "rocket.collector")
public record CollectorProperties(String baseUrl, String sharedSecret, Duration connectTimeout, Duration readTimeout) {
    public CollectorProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:8100";
        if (sharedSecret == null) sharedSecret = "";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(2);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(15);
    }
}
