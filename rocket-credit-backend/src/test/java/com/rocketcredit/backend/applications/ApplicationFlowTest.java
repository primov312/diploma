package com.rocketcredit.backend.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.analysis.AnalysisDecision;
import com.rocketcredit.backend.analysis.AnalysisUnavailableException;
import com.rocketcredit.backend.analysis.FeatureBundle;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;

/**
 * Backend side of the request -> score -> save -> read flow. The analysis service is mocked
 * here (its rules are tested in Python); what this class proves is validation, the feature
 * bundle that leaves the backend, atomic persistence, ownership and the failure behaviour.
 */
class ApplicationFlowTest extends AbstractIntegrationTest {

    @MockBean AnalysisClient analysis;
    @Autowired CreditApplicationRepository applications;

    private static final String DEMO_PASSWORD = "rocket-demo-123";

    private static AnalysisDecision decision(String status, String score, String possible, List<String> reasons) {
        return new AnalysisDecision(status, new BigDecimal(score), new BigDecimal(possible), "USD", reasons,
                Map.of("profile", new AnalysisDecision.Factor(1.0, 0.4, List.of(), Map.of()),
                       "history", new AnalysisDecision.Factor(0.85, 0.6, List.of(), Map.of("ordersScore", 1.0)),
                       "affordability", new AnalysisDecision.Factor(null, 0.0, List.of(), Map.of("partnerCap", 1500.0))),
                "rules-v1", false, "NOT_REQUESTED", null, Map.of());
    }

    @BeforeEach
    void defaultDecision() {
        when(analysis.score(any())).thenReturn(decision("APPROVED", "0.9130", "1500.00", List.of()));
    }

    @Test
    void submitScoresSavesAndReadsBack() throws Exception {
        MockHttpSession avery = login("avery@demo.rocket.local", DEMO_PASSWORD);

        var result = mvc.perform(postJson("/api/applications",
                        "{\"partnerSlug\":\"markethub\",\"requestedAmount\":300.00,\"useAi\":false}", fetchCsrf()).session(avery))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/applications/")))
                .andExpect(jsonPath("$.decisionStatus").value("APPROVED"))
                .andExpect(jsonPath("$.score").value(0.913))
                .andExpect(jsonPath("$.possibleAmount").value(1500.00))
                .andExpect(jsonPath("$.partnerSlug").value("markethub"))
                .andExpect(jsonPath("$.policyVersion").value("rules-v1"))
                .andExpect(jsonPath("$.aiStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.preparationMode").value("SEQUENTIAL"))
                .andExpect(jsonPath("$.featureSnapshot.history.partnerOrders12m").value(7))   // 8 orders, 1 refunded
                .andExpect(jsonPath("$.featureSnapshot.finance.monthlyIncome").value(4500.00))
                .andReturn();
        int id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        // the bundle that left the backend carries derived features only
        var captor = ArgumentCaptor.forClass(FeatureBundle.class);
        verify(analysis).score(captor.capture());
        FeatureBundle sent = captor.getValue();
        assertThat(sent.requestedAmount()).isEqualByComparingTo("300.00");
        assertThat(sent.partnerCap()).isEqualByComparingTo("1500.00");
        assertThat(sent.profile().accountAgeMonths()).isGreaterThanOrEqualTo(29);
        assertThat(sent.history().partnerOrders12m()).isEqualTo(7);
        assertThat(sent.history().partnerRefundRate()).isEqualTo(0.125);
        assertThat(sent.history().partnerOnTimeRatio()).isEqualTo(1.0);
        assertThat(sent.history().totalOrders12m()).isEqualTo(24);        // 25 purchases, 1 refunded
        assertThat(sent.finance().monthlyObligations()).isEqualByComparingTo("300.00");
        assertThat(sent.toString()).doesNotContain("avery").doesNotContain("@");

        // persisted and readable by the owner: list + detail
        mvc.perform(get("/api/applications").session(avery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(id), hasSize(1)))
                .andExpect(jsonPath("$[0].featureSnapshot").doesNotExist());
        mvc.perform(get("/api/applications/" + id).session(avery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasons").isArray())
                .andExpect(jsonPath("$.factors.history.details.ordersScore").value(1.0))
                .andExpect(jsonPath("$.featureSnapshot.observedAt").exists());

        var row = applications.findById((long) id).orElseThrow();
        assertThat(row.getFeatureSnapshot()).containsKeys("profile", "history", "finance", "requestedAmount");
        assertThat(row.getFactors()).containsKey("affordability");
    }

    @Test
    void productBasedRequestUsesTheStoredPrice() throws Exception {
        MockHttpSession casey = login("casey@demo.rocket.local", DEMO_PASSWORD);
        String partner = mvc.perform(get("/api/partners/threadly")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(partner, "$.products[?(@.fixtureId == 'threadly-wool-coat')].id");
        int coatId = ids.get(0);

        // query-string style price tampering is ignored: the amount must equal the stored price
        mvc.perform(postJson("/api/applications",
                        "{\"partnerSlug\":\"threadly\",\"productId\":%d,\"requestedAmount\":1.00}".formatted(coatId), fetchCsrf()).session(casey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("AMOUNT_MISMATCH"));

        mvc.perform(postJson("/api/applications",
                        "{\"partnerSlug\":\"threadly\",\"productId\":%d}".formatted(coatId), fetchCsrf()).session(casey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestedAmount").value(189.00))
                .andExpect(jsonPath("$.productName").value("Wool coat"));

        // a product from another partner is refused
        mvc.perform(postJson("/api/applications",
                        "{\"partnerSlug\":\"streambox\",\"productId\":%d}".formatted(coatId), fetchCsrf()).session(casey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNKNOWN_PRODUCT"));
    }

    @Test
    void invalidInputsAreRejectedBeforeScoring() throws Exception {
        MockHttpSession riley = login("riley@demo.rocket.local", DEMO_PASSWORD);
        long before = applications.count();
        String[] bodies = {
                "{\"partnerSlug\":\"markethub\",\"requestedAmount\":-5}",
                "{\"partnerSlug\":\"markethub\",\"requestedAmount\":0}",
                "{\"partnerSlug\":\"markethub\",\"requestedAmount\":10.123}",
                "{\"partnerSlug\":\"markethub\"}",
                "{\"partnerSlug\":\"nope\",\"requestedAmount\":10}",
                "{\"requestedAmount\":10}",
                "not json",
        };
        for (String body : bodies) {
            mvc.perform(postJson("/api/applications", body, fetchCsrf()).session(riley))
                    .andExpect(status().isBadRequest());
        }
        verify(analysis, never()).score(any());
        assertThat(applications.count()).isEqualTo(before);

        mvc.perform(postJson("/api/applications", "{\"partnerSlug\":\"markethub\",\"requestedAmount\":10}", fetchCsrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analysisFailureIsATechnicalErrorAndNothingIsSaved() throws Exception {
        MockHttpSession drew = login("drew@demo.rocket.local", DEMO_PASSWORD);
        when(analysis.score(any())).thenThrow(new AnalysisUnavailableException("analysis /score failed: SocketTimeoutException"));

        mvc.perform(postJson("/api/applications", "{\"partnerSlug\":\"threadly\",\"requestedAmount\":150}", fetchCsrf()).session(drew))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("ANALYSIS_UNAVAILABLE"))
                .andExpect(jsonPath("$.decisionStatus").doesNotExist());

        assertThat(applications.findByUserIdOrderByCreatedAtDesc(userId("drew"))).isEmpty();
    }

    @Test
    void applicationsAreVisibleToTheirOwnerOnly() throws Exception {
        MockHttpSession avery = login("avery@demo.rocket.local", DEMO_PASSWORD);
        MockHttpSession casey = login("casey@demo.rocket.local", DEMO_PASSWORD);

        String body = mvc.perform(postJson("/api/applications", "{\"partnerSlug\":\"streambox\",\"requestedAmount\":50}", fetchCsrf()).session(avery))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/applications/" + id).session(casey)).andExpect(status().isNotFound());
        mvc.perform(get("/api/applications").session(casey))
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(id), hasSize(0)));
        mvc.perform(get("/api/applications/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void reviewAndRejectionResultsArePersistedAsReturned() throws Exception {
        MockHttpSession riley = login("riley@demo.rocket.local", DEMO_PASSWORD);
        when(analysis.score(any())).thenReturn(decision("REVIEW", "0.5210", "900.00", List.of("PROFILE_INCOMPLETE", "SCORE_INCONCLUSIVE")));
        mvc.perform(postJson("/api/applications", "{\"partnerSlug\":\"markethub\",\"requestedAmount\":250}", fetchCsrf()).session(riley))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisionStatus").value("REVIEW"))
                .andExpect(jsonPath("$.reasons", hasSize(2)));

        when(analysis.score(any())).thenReturn(decision("REJECTED", "0.7000", "600.00", List.of("AMOUNT_ABOVE_POSSIBLE_AMOUNT")));
        mvc.perform(postJson("/api/applications", "{\"partnerSlug\":\"markethub\",\"requestedAmount\":700}", fetchCsrf()).session(riley))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisionStatus").value("REJECTED"))
                .andExpect(jsonPath("$.possibleAmount").value(600.00));   // suggestion, not an offer
    }

    private Long userId(String who) {
        return users.findByEmail(who + "@demo.rocket.local").orElseThrow().getId();
    }

    @Autowired com.rocketcredit.backend.users.UserRepository users;
}
