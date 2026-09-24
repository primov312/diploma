package com.rocketcredit.backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final RestClient longHttp;

    @Autowired
    public AnalysisClient(@Qualifier("analysisRestClient") RestClient analysisRestClient,
                          @Qualifier("analysisLongRestClient") RestClient analysisLongRestClient) {
        this.http = analysisRestClient;
        this.longHttp = analysisLongRestClient;
    }

    AnalysisClient(RestClient analysisRestClient) {
        this.http = analysisRestClient;
        this.longHttp = analysisRestClient;
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

    public AffordabilityResult affordability(AffordabilityRequest request) {
        try {
            var body = http.post().uri("/affordability")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AffordabilityResult.class);
            if (body == null) throw new AnalysisUnavailableException("analysis /affordability returned an empty body");
            return body;
        } catch (ResourceAccessException | RestClientResponseException e) {
            log.warn("analysis /affordability failed: {}", describe(e));
            throw new AnalysisUnavailableException("analysis /affordability failed: " + describe(e), e);
        }
    }

    public JsonNode runDemoAnalysis(String kind, String scenarioId, String declaredDistrict) {
        String route = switch (kind) {
            case "LOCATION" -> "/demo-analysis/location";
            case "SOCIAL" -> "/demo-analysis/social";
            default -> throw new IllegalArgumentException("unsupported demo analysis kind");
        };
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("scenarioId", scenarioId);
            if ("LOCATION".equals(kind) && declaredDistrict != null) body.put("declaredDistrict", declaredDistrict);
            JsonNode response = longHttp.post().uri(route).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
            if (response == null) throw new AnalysisUnavailableException("analysis " + route + " returned an empty body");
            return response;
        } catch (ResourceAccessException | RestClientResponseException e) {
            log.warn("analysis {} failed: {}", route, describe(e));
            throw new AnalysisUnavailableException("analysis " + route + " failed: " + describe(e), e);
        }
    }

    public JsonNode extractDemoAddress(String imageData, String mimeType) {
        try {
            JsonNode response = longHttp.post().uri("/demo-analysis/address")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("imageData", imageData, "mimeType", mimeType))
                    .retrieve().body(JsonNode.class);
            if (response == null) throw new AnalysisUnavailableException("analysis address extraction returned an empty body");
            return response;
        } catch (ResourceAccessException | RestClientResponseException e) {
            log.warn("analysis address extraction failed: {}", describe(e));
            throw new AnalysisUnavailableException("analysis address extraction failed: " + describe(e), e);
        }
    }

    public JsonNode extractLocalCosts(String districtId) {
        try {
            JsonNode response = longHttp.post().uri("/demo-analysis/local-costs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("districtId", districtId))
                    .retrieve().body(JsonNode.class);
            if (response == null) throw new AnalysisUnavailableException("local-cost extraction returned an empty body");
            return response;
        } catch (ResourceAccessException | RestClientResponseException e) {
            log.warn("local-cost extraction failed: {}", describe(e));
            throw new AnalysisUnavailableException("local-cost extraction failed: " + describe(e), e);
        }
    }

    private static String describe(Exception e) {
        if (e instanceof RestClientResponseException r) {
            return "HTTP " + r.getStatusCode().value();
        }
        return e.getClass().getSimpleName();
    }
}
