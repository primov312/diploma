package com.rocketcredit.backend.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client for the Python analysis service. Every call carries the
 * shared secret in {@code X-Analysis-Token}. Failures become
 * {@link AnalysisUnavailableException}; the caller decides what that means.
 */
@Component
public class AnalysisClient {
    private static final Logger log = LoggerFactory.getLogger(AnalysisClient.class);
    static final String TOKEN_HEADER = "X-Analysis-Token";

    private final RestClient http;

    public AnalysisClient(RestClient analysisRestClient) {
        this.http = analysisRestClient;
    }

    public AnalysisHealth health() {
        try {
            var body = http.get().uri("/health").retrieve().body(AnalysisHealth.class);
            if (body == null) throw new AnalysisUnavailableException("analysis /health returned an empty body");
            return body;
        } catch (ResourceAccessException | RestClientResponseException e) {
            throw new AnalysisUnavailableException("analysis /health failed: " + describe(e), e);
        }
    }

    public AnalysisDecision score(FeatureBundle bundle) {
        try {
            var body = http.post().uri("/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bundle)
                    .retrieve()
                    .body(AnalysisDecision.class);
            if (body == null) throw new AnalysisUnavailableException("analysis /score returned an empty body");
            return body;
        } catch (ResourceAccessException | RestClientResponseException e) {
            // The bundle is not logged: it carries the customer's derived financial features.
            log.warn("analysis /score failed: {}", describe(e));
            throw new AnalysisUnavailableException("analysis /score failed: " + describe(e), e);
        }
    }

    private static String describe(Exception e) {
        if (e instanceof RestClientResponseException r) {
            return "HTTP " + r.getStatusCode().value();
        }
        return e.getClass().getSimpleName();
    }
}
