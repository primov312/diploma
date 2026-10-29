// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.rocketcredit.backend.common.ApiException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client for the footprint collector. The collector enforces the allow-list and robots.txt; this client
 * only turns its answers into API errors the customer can understand. The address the customer typed is never logged.
 */
@Component
public class CollectorClient {
    private static final Logger log = LoggerFactory.getLogger(CollectorClient.class);
    static final String TOKEN_HEADER = "X-Collector-Token";

    private final RestClient http;

    @Autowired
    public CollectorClient(@Qualifier("collectorRestClient") RestClient collectorRestClient) {
        this.http = collectorRestClient;
    }

    /** @return {@code {source:{url,host,format,robots,fetchedAt}, posts:[...], skipped:n}} */
    public JsonNode collect(String url) {
        try {
            JsonNode body = http.post().uri("/collect").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("url", url)).retrieve().body(JsonNode.class);
            if (body == null) throw unavailable();
            return body;
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            log.warn("collector unreachable: {}", e.getClass().getSimpleName());
            throw unavailable();
        }
    }

    /**
     * Liveness of the collector ({@code GET /health}, no token needed). Used only by {@link CollectorHealthIndicator};
     * returns the collector's response format, or throws {@code COLLECTOR_UNAVAILABLE} when it is down or not ok.
     * (AI-assisted (Claude), 2026; reviewed by the author.)
     */
    public String health() {
        try {
            JsonNode body = http.get().uri("/health").retrieve().body(JsonNode.class);
            if (body == null || !"ok".equals(body.path("status").asText())) throw unavailable();
            return body.path("format").asText("unknown");
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "COLLECTOR_UNAVAILABLE",
                "The page reader is not available right now. Try again later.");
    }

    /** The collector answers {@code {code, message}}; its codes are part of the API contract. */
    private static ApiException translate(RestClientResponseException e) {
        String code = "COLLECTOR_FAILED";
        String message = "The page could not be read.";
        try {
            JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getResponseBodyAsString());
            if (body.hasNonNull("code")) code = body.get("code").asText();
            if (body.hasNonNull("message")) message = body.get("message").asText();
        } catch (Exception ignored) {
            // keep the generic answer
        }
        int status = e.getStatusCode().value();
        if (status == 401 || status >= 500 && !"SOURCE_TOO_LARGE".equals(code) && !"FETCH_FAILED".equals(code)) return unavailable();
        HttpStatus mapped = switch (code) {
            case "INVALID_URL" -> HttpStatus.BAD_REQUEST;
            case "FETCH_FAILED", "SOURCE_TOO_LARGE" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.UNPROCESSABLE_ENTITY; // SOURCE_NOT_ALLOWED, ROBOTS_DISALLOWED, NO_POSTS, UNSUPPORTED_CONTENT
        };
        return new ApiException(mapped, code, message);
    }
}
