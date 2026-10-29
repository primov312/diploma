// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.consents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.analysis.AnalysisDecision;
import com.rocketcredit.backend.signals.CollectorClient;
import com.rocketcredit.backend.signals.DemoSignalService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Consent is recorded, enforced before processing, auditable, and withdrawing it erases what it covered. */
class ConsentIntegrationTest extends AbstractIntegrationTest {
    @MockBean AnalysisClient analysis;
    @MockBean CollectorClient collector;
    @Autowired DemoSignalService signals;
    @Autowired ObjectMapper mapper;

    private long userId(String email) {
        return testJdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private MockHttpSession newCustomerWithoutConsent(String email) throws Exception {
        registerWithoutConsent(email, "password-123");
        return login(email, "password-123");
    }

    private void setConsent(MockHttpSession session, String purpose, boolean granted) throws Exception {
        mvc.perform(withCsrf(put("/api/me/consents/" + purpose).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":" + granted + "}"), fetchCsrf()))
                .andExpect(status().isOk());
    }

    private int auditRows(long userId, String purpose) {
        return testJdbc.queryForObject("SELECT count(*) FROM consent_records WHERE user_id=? AND purpose=?", Integer.class, userId, purpose);
    }

    @Test
    void newAccountHasNoConsentAndSeesTheTextsToAgreeTo() throws Exception {
        var session = newCustomerWithoutConsent(uniqueEmail());

        mvc.perform(get("/api/me/consents").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyVersion").value("consent-v1"))
                .andExpect(jsonPath("$.consents.length()").value(3))
                .andExpect(jsonPath("$.consents[0].purpose").value("DATA_PROCESSING"))
                .andExpect(jsonPath("$.consents[0].requiredForCredit").value(true))
                .andExpect(jsonPath("$.consents[0].granted").value(false))
                .andExpect(jsonPath("$.consents[1].granted").value(false))
                .andExpect(jsonPath("$.consents[2].granted").value(false))
                .andExpect(jsonPath("$.consents[0].text").isNotEmpty());
    }

    @Test
    void consentEndpointsNeedASession() throws Exception {
        mvc.perform(get("/api/me/consents")).andExpect(status().isUnauthorized());
    }

    @Test
    void creditRequestIsRefusedUntilDataProcessingIsAgreed() throws Exception {
        var session = newCustomerWithoutConsent(uniqueEmail());
        String body = "{\"partnerSlug\":\"markethub\",\"requestedAmount\":300.00,\"useAi\":false,\"quoteId\":1}";

        mvc.perform(postJson("/api/applications", body, fetchCsrf()).session(session).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CONSENT_REQUIRED"));
    }

    @Test
    void aiEstimateNeedsItsOwnConsent() throws Exception {
        String email = uniqueEmail();
        var session = newCustomerWithoutConsent(email);
        setConsent(session, "DATA_PROCESSING", true);
        String body = "{\"partnerSlug\":\"markethub\",\"requestedAmount\":300.00,\"useAi\":true,\"quoteId\":1}";

        mvc.perform(postJson("/api/applications", body, fetchCsrf()).session(session).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CONSENT_REQUIRED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("AI-assisted analysis")));
    }

    @Test
    void digitalFootprintRunsAreRefusedUntilAgreed() throws Exception {
        var session = newCustomerWithoutConsent(uniqueEmail());

        for (String route : List.of("social-runs", "location-runs")) {
            mvc.perform(postJson("/api/me/demo-signals/" + route, "{\"scenarioId\":\"ordinary\"}", fetchCsrf()).session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("CONSENT_REQUIRED"));
        }
    }

    @Test
    void everyGrantAndWithdrawalIsAnAuditRowAndRepeatsAddNothing() throws Exception {
        String email = uniqueEmail();
        var session = newCustomerWithoutConsent(email);
        long userId = userId(email);

        setConsent(session, "AI_ANALYSIS", true);
        setConsent(session, "AI_ANALYSIS", true);          // same decision again: no new row
        assertThat(auditRows(userId, "AI_ANALYSIS")).isEqualTo(1);
        setConsent(session, "AI_ANALYSIS", false);
        setConsent(session, "AI_ANALYSIS", true);
        assertThat(auditRows(userId, "AI_ANALYSIS")).isEqualTo(3);

        var row = testJdbc.queryForMap("SELECT policy_version, text_sha256, source FROM consent_records WHERE user_id=? ORDER BY id LIMIT 1", userId);
        assertThat(row.get("policy_version")).isEqualTo("consent-v1");
        assertThat(((String) row.get("text_sha256")).trim()).isEqualTo(ConsentPolicy.text(ConsentPurpose.AI_ANALYSIS).sha256());
        assertThat(row.get("source")).isEqualTo("USER");
    }

    @Test
    void aGrantUnderAnOlderTextDoesNotCount() throws Exception {
        String email = uniqueEmail();
        newCustomerWithoutConsent(email);
        long userId = userId(email);
        testJdbc.update("INSERT INTO consent_records (user_id, purpose, granted, policy_version, text_sha256, source) VALUES (?, 'DATA_PROCESSING', TRUE, 'consent-v0', repeat('0', 64), 'USER')", userId);

        assertThat(consentService.has(userId, ConsentPurpose.DATA_PROCESSING)).isFalse();
        assertThat(consentService.view(userId).consents().get(0).current()).isFalse();
    }

    @Test
    void unknownPurposeIsABadRequest() throws Exception {
        var session = newCustomerWithoutConsent(uniqueEmail());
        mvc.perform(withCsrf(put("/api/me/consents/MARKETING").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":true}"), fetchCsrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdrawingDigitalFootprintErasesReportsAndCancelsRunsInFlight() throws Exception {
        String email = uniqueEmail();
        var session = newCustomerWithoutConsent(email);
        long userId = userId(email);
        setConsent(session, "DIGITAL_FOOTPRINT", true);
        when(analysis.runDemoAnalysis(eq("SOCIAL"), eq("ordinary"), nullable(String.class)))
                .thenReturn(mapper.readTree("{\"status\":\"COMPLETE\",\"dataSource\":\"SYNTHETIC\",\"analysisMode\":\"FIXTURE\"}"));
        assertThat(signals.run(userId, "SOCIAL", "ordinary").state()).isEqualTo("SUCCEEDED");
        assertThat(signals.reports(userId, "SOCIAL")).hasSize(1);
        long generation = signals.settings(userId).permissionGeneration();

        setConsent(session, "DIGITAL_FOOTPRINT", false);

        assertThat(signals.reports(userId, "SOCIAL")).isEmpty();
        assertThat(signals.settings(userId).permissionGeneration()).isGreaterThan(generation);
        assertThat(consentService.has(userId, ConsentPurpose.DIGITAL_FOOTPRINT)).isFalse();
        mvc.perform(postJson("/api/me/demo-signals/social-runs", "{\"scenarioId\":\"ordinary\"}", fetchCsrf()).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicPageRunStoresTheReportWithItsCollectionEvidence() throws Exception {
        String email = uniqueEmail();
        var session = newCustomerWithoutConsent(email);
        long userId = userId(email);
        setConsent(session, "DIGITAL_FOOTPRINT", true);
        when(collector.collect("http://collector:8100/demo-source/ordinary")).thenReturn(mapper.readTree("""
                {"source":{"url":"http://collector:8100/demo-source/ordinary","host":"collector","format":"rocket-profile-v1",
                 "robots":"ALLOWED","fetchedAt":"2026-10-27T10:00:00Z"},
                 "posts":[{"id":"post-01","date":"2026-08-02","location":null,"text":"Hello","reactions":1,"comments":[]}],"skipped":0}"""));
        when(analysis.runSocialAccountAnalysis(any(), eq("PUBLIC_PAGE"))).thenReturn(mapper.readTree(
                "{\"status\":\"COMPLETE\",\"dataSource\":\"PUBLIC_PAGE\",\"analysisMode\":\"FIXTURE\",\"scenarioId\":\"public-page\"}"));

        var result = signals.run(userId, "SOCIAL", "public-page", "http://collector:8100/demo-source/ordinary");

        assertThat(result.state()).isEqualTo("SUCCEEDED");
        assertThat(result.report().path("collection").path("robots").asText()).isEqualTo("ALLOWED");
        assertThat(result.report().path("collection").path("host").asText()).isEqualTo("collector");
        assertThat(testJdbc.queryForObject("SELECT data_source FROM demo_signal_reports WHERE user_id=?", String.class, userId)).isEqualTo("PUBLIC_PAGE");
    }

    @Test
    void publicPageRunNeedsAnAddress() throws Exception {
        String email = uniqueEmail();
        var session = newCustomerWithoutConsent(email);
        setConsent(session, "DIGITAL_FOOTPRINT", true);

        mvc.perform(postJson("/api/me/demo-signals/social-runs", "{\"scenarioId\":\"public-page\"}", fetchCsrf()).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SOURCE_URL_REQUIRED"));
    }

    @Test
    void demoPersonasStartWithConsentRecordedAsFixtures() throws Exception {
        long avery = userId("avery@demo.rocket.local");
        assertThat(consentService.has(avery, ConsentPurpose.DATA_PROCESSING)).isTrue();
        assertThat(testJdbc.queryForObject("SELECT DISTINCT source FROM consent_records WHERE user_id=?", String.class, avery)).isEqualTo("DEMO_FIXTURE");
    }

    @Test
    void decisionStoresTheConsentsInForceAtThatMoment() throws Exception {
        when(analysis.score(any())).thenReturn(new AnalysisDecision("APPROVED", new BigDecimal("0.9130"), new BigDecimal("1500.00"), "USD", List.of(),
                Map.of("profile", new AnalysisDecision.Factor(1.0, 0.4, List.of(), Map.of()),
                       "history", new AnalysisDecision.Factor(0.85, 0.6, List.of(), Map.of("ordersScore", 1.0)),
                       "affordability", new AnalysisDecision.Factor(null, 0.0, List.of(), Map.of("partnerCap", 1500.0))),
                "rules-v1", false, "NOT_REQUESTED", null, Map.of()));
        MockHttpSession avery = login("avery@demo.rocket.local", "rocket-demo-123");
        var quote = mvc.perform(postJson("/api/credit-quotes",
                        "{\"partnerSlug\":\"markethub\",\"requestedAmount\":300.00,\"termUnit\":\"MONTH\",\"installmentCount\":6}", fetchCsrf()).session(avery))
                .andExpect(status().isOk()).andReturn();
        long quoteId = mapper.readTree(quote.getResponse().getContentAsString()).path("id").asLong();

        var created = mvc.perform(postJson("/api/applications",
                        "{\"partnerSlug\":\"markethub\",\"requestedAmount\":300.00,\"useAi\":false,\"quoteId\":" + quoteId + "}", fetchCsrf())
                        .session(avery).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consent.policyVersion").value("consent-v1"))
                .andExpect(jsonPath("$.consent.DATA_PROCESSING.granted").value(true))
                .andExpect(jsonPath("$.consent.DIGITAL_FOOTPRINT.granted").value(true))
                .andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();

        mvc.perform(get("/api/applications/" + id).session(avery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consent.AI_ANALYSIS.granted").value(true));
    }
}
