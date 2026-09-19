package com.rocketcredit.backend.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AnalysisClientTest {
    private MockRestServiceServer server;
    private AnalysisClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AnalysisClient(builder.baseUrl("http://analysis:8000")
                .defaultHeader(AnalysisClient.TOKEN_HEADER, "s3cret").build());
    }

    private static FeatureBundle bundle() {
        return new FeatureBundle(
                new BigDecimal("300.00"), "USD", new BigDecimal("1500.00"), false,
                OffsetDateTime.parse("2026-09-19T10:00:00Z"),
                new FeatureBundle.Profile(30, true, true),
                new FeatureBundle.History(18, new BigDecimal("125.00"), 0.02, 0.98, 36, 40),
                new FeatureBundle.Finance(new BigDecimal("4500.00"), new BigDecimal("2500.00"), new BigDecimal("300.00")));
    }

    @Test
    void healthParsesPolicyVersionAndSendsToken() {
        server.expect(requestTo("http://analysis:8000/health"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Analysis-Token", "s3cret"))
                .andRespond(withSuccess("{\"status\":\"ok\",\"policyVersion\":\"rules-v1\",\"modelVersion\":null,\"modelLoaded\":false}",
                        MediaType.APPLICATION_JSON));

        var h = client.health();
        assertThat(h.status()).isEqualTo("ok");
        assertThat(h.policyVersion()).isEqualTo("rules-v1");
        server.verify();
    }

    @Test
    void scoreSendsDerivedFeaturesOnlyAndParsesDecision() {
        server.expect(requestTo("http://analysis:8000/score"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Analysis-Token", "s3cret"))
                .andExpect(jsonPath("$.requestedAmount").value(300.00))
                .andExpect(jsonPath("$.profile.accountAgeMonths").value(30))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andRespond(withSuccess("""
                        {"decisionStatus":"APPROVED","score":0.8123,"possibleAmount":"1500.00","currency":"USD",
                         "reasons":[],"factors":{"profile":{"score":1.0,"weight":0.4,"reasons":[],"details":{}}},
                         "policyVersion":"rules-v1","aiRequested":false,"aiStatus":"NOT_REQUESTED","modelVersion":null,
                         "aiContributions":{}}
                        """, MediaType.APPLICATION_JSON));

        var d = client.score(bundle());
        assertThat(d.decisionStatus()).isEqualTo("APPROVED");
        assertThat(d.possibleAmount()).isEqualByComparingTo("1500.00");
        assertThat(d.factors()).containsKey("profile");
        assertThat(d.policyVersion()).isEqualTo("rules-v1");
        server.verify();
    }

    @Test
    void serverErrorBecomesAnalysisUnavailable() {
        server.expect(requestTo("http://analysis:8000/score"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.score(bundle()))
                .isInstanceOf(AnalysisUnavailableException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void rejectedTokenBecomesAnalysisUnavailable() {
        server.expect(requestTo("http://analysis:8000/health"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(client::health)
                .isInstanceOf(AnalysisUnavailableException.class)
                .hasMessageContaining("HTTP 401");
    }
}
