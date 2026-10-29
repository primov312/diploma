// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.analysis.AnalysisDecision;
import com.rocketcredit.backend.auth.AuthenticatedUser;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.users.Role;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Account management, the human review of REVIEW decisions, partners and the audit trail. */
class AdminIntegrationTest extends AbstractIntegrationTest {
    @MockBean AnalysisClient analysis;
    @MockBean com.rocketcredit.backend.signals.CollectorClient collector;
    @Autowired AdminAccountService accounts;
    @Autowired PasswordEncoder encoder;

    private MockHttpSession admin;
    private long adminId;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginDemo("admin");
        adminId = id("admin@demo.rocket.local");
        review("0.5210", "300.00");
    }

    private void review(String score, String possible) {
        when(analysis.score(any())).thenReturn(new AnalysisDecision("REVIEW", new BigDecimal(score), new BigDecimal(possible), "USD",
                List.of("PROFILE_INCOMPLETE", "SCORE_INCONCLUSIVE"),
                Map.of("profile", new AnalysisDecision.Factor(0.4, 0.4, List.of("PROFILE_INCOMPLETE"), Map.of("accountAgeScore", 0.3)),
                       "affordability", new AnalysisDecision.Factor(null, 0.0, List.of(), Map.of("monthlyNetIncome", 2500.0))),
                "rules-v4", false, "NOT_REQUESTED", null, Map.of()));
    }

    private long id(String email) {
        return testJdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private long auditCount(String action, String target) {
        return testJdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action=? AND target_id=? AND actor_user_id=?",
                Long.class, action, target, adminId);
    }

    /** A customer request for 250 USD at Amazon that the (mocked) assessment leaves in REVIEW. */
    private long reviewApplication(MockHttpSession customer) throws Exception {
        String quote = mvc.perform(postJson("/api/credit-quotes",
                        "{\"partnerSlug\":\"markethub\",\"requestedAmount\":250,\"termUnit\":\"MONTH\",\"installmentCount\":6}", fetchCsrf())
                        .session(customer)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String body = mvc.perform(postJson("/api/applications", "{\"partnerSlug\":\"markethub\",\"requestedAmount\":250,\"useAi\":false,\"quoteId\":%d}"
                                .formatted(((Number) JsonPath.read(quote, "$.id")).longValue()), fetchCsrf())
                        .session(customer).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.decisionStatus").value("REVIEW"))
                .andExpect(jsonPath("$.reviewState").value("OPEN")).andExpect(jsonPath("$.canAccept").value(false))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions decide(long application, String body) throws Exception {
        return mvc.perform(postJson("/api/admin/reviews/" + application + "/decision", body, fetchCsrf()).session(admin));
    }

    // ---- accounts --------------------------------------------------------------------------------

    @Test
    void createSellerRejectsDuplicatesAndNeverReturnsThePassword() throws Exception {
        long partner = createPartner("300");
        String email = uniqueEmail();
        String body = "{\"email\":\"%s\",\"displayName\":\"New Seller\",\"partnerId\":%d,\"password\":\"seller-pass-1\"}".formatted(email, partner);
        String created = mvc.perform(postJson("/api/admin/users/sellers", body, fetchCsrf()).session(admin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("SELLER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContainIgnoringCase("password").doesNotContain("$2").doesNotContain("seller-pass-1");
        // a double submission hits the unique e-mail and gets the same answer
        mvc.perform(postJson("/api/admin/users/sellers", body.replace(email, email.toUpperCase()), fetchCsrf()).session(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));
        long seller = id(email);
        assertThat(auditCount("ADMIN_SELLER_CREATED", String.valueOf(seller))).isEqualTo(1);
        assertThat(testJdbc.queryForObject("SELECT partner_id FROM seller_accounts WHERE user_id=?", Long.class, seller)).isEqualTo(partner);
        assertThat(testJdbc.queryForObject("SELECT count(*) FROM wallet_accounts WHERE user_id=?", Long.class, seller)).isZero();

        mvc.perform(postJson("/api/admin/users/sellers", body.replace(email, uniqueEmail()).replace("\"partnerId\":" + partner, "\"partnerId\":999999"), fetchCsrf())
                .session(admin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("UNKNOWN_PARTNER"));
        mvc.perform(postJson("/api/admin/users/sellers", body.replace(email, uniqueEmail()).replace("seller-pass-1", "short"), fetchCsrf())
                .session(admin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.password").exists());

        mvc.perform(get("/api/admin/users?role=SELLER&q=" + email.substring(0, 12)).session(admin))
                .andExpect(jsonPath("$.items[0].email").value(email)).andExpect(jsonPath("$.items[0].partnerSlug").exists());
    }

    @Test
    void anAdministratorCannotDisableTheirOwnAccount() throws Exception {
        mvc.perform(postJson("/api/admin/users/" + adminId + "/disable", "{}", fetchCsrf()).session(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("OWN_ACCOUNT"));
        assertThat(testJdbc.queryForObject("SELECT enabled FROM users WHERE id=?", Boolean.class, adminId)).isTrue();
    }

    /**
     * Through the API the acting administrator is always an enabled administrator, so the last-admin rule
     * only matters when two administrators disable each other at the same moment. This test calls the service
     * as an actor whose own account was disabled a moment earlier.
     */
    @Test
    void theLastEnabledAdministratorCannotBeDisabled() {
        String hash = encoder.encode("admin-pass-1");
        long target = testJdbc.queryForObject("INSERT INTO users(email,password_hash,display_name,role) VALUES (?,?,?,'ADMIN') RETURNING id",
                Long.class, uniqueEmail(), hash, "Admin T");
        long actor = testJdbc.queryForObject("INSERT INTO users(email,password_hash,display_name,role,enabled) VALUES (?,?,?,'ADMIN',false) RETURNING id",
                Long.class, uniqueEmail(), hash, "Admin A");
        var others = testJdbc.queryForList("SELECT id FROM users WHERE role='ADMIN' AND enabled AND id<>?", Long.class, target);
        try {
            others.forEach(other -> testJdbc.update("UPDATE users SET enabled=false WHERE id=?", other));
            var principal = new AuthenticatedUser(actor, "a@x", null, Role.ADMIN, null, true);
            assertThatThrownBy(() -> accounts.setEnabled(principal, target, false))
                    .isInstanceOf(ApiException.class).extracting("code").isEqualTo("LAST_ADMIN");
            assertThat(testJdbc.queryForObject("SELECT enabled FROM users WHERE id=?", Boolean.class, target)).isTrue();
        } finally {
            others.forEach(other -> testJdbc.update("UPDATE users SET enabled=true WHERE id=?", other));
        }
    }

    @Test
    void passwordResetReplacesTheOldPassword() throws Exception {
        String email = uniqueEmail();
        register(email, "old-password-1");
        long user = id(email);
        String response = mvc.perform(postJson("/api/admin/users/" + user + "/reset-password", "{\"password\":\"new-password-1\"}", fetchCsrf())
                .session(admin)).andExpect(status().isNoContent()).andReturn().getResponse().getContentAsString();
        assertThat(response).isEmpty();
        mvc.perform(postJson("/api/auth/login", loginJson(email, "old-password-1"), fetchCsrf())).andExpect(status().isUnauthorized());
        login(email, "new-password-1");
        assertThat(auditCount("ADMIN_PASSWORD_RESET", String.valueOf(user))).isEqualTo(1);
        assertThat(testJdbc.queryForObject("SELECT details::text FROM audit_events WHERE action='ADMIN_PASSWORD_RESET' AND target_id=?",
                String.class, String.valueOf(user))).doesNotContain("password").doesNotContain("$2");
    }

    // ---- human review ----------------------------------------------------------------------------

    @Test
    void approvedReviewIsDecidedOnceAndTheLoanUsesTheApprovedAmount() throws Exception {
        String email = uniqueEmail();
        MockHttpSession customer = registerAndLogin(email);
        long app = reviewApplication(customer);
        var aiBefore = testJdbc.queryForMap("SELECT decision_status, score, possible_amount, reasons::text, factors::text FROM credit_applications WHERE id=?", app);

        String open = mvc.perform(get("/api/admin/reviews?state=OPEN&size=100").session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(open, "$.items[*].applicationId")).contains((int) app);
        String detail = mvc.perform(get("/api/admin/reviews/" + app).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reasons[0]").value("PROFILE_INCOMPLETE"))
                .andExpect(jsonPath("$.factors.profile.score").value(0.4))
                .andExpect(jsonPath("$.plan.installmentCount").value(6))
                .andExpect(jsonPath("$.decision").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain("featureSnapshot").doesNotContain("monthlyNetIncome").doesNotContain("details")
                .doesNotContainIgnoringCase("consent").doesNotContainIgnoringCase("address").doesNotContain(email);

        decide(app, "{\"outcome\":\"APPROVED\",\"approvedAmount\":200,\"note\":\"short\"}").andExpect(status().isBadRequest());
        decide(app, "{\"outcome\":\"APPROVED\",\"approvedAmount\":200}").andExpect(status().isBadRequest());
        decide(app, "{\"outcome\":\"APPROVED\",\"note\":\"Income documents look consistent.\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_APPROVED_AMOUNT"));
        decide(app, "{\"outcome\":\"APPROVED\",\"approvedAmount\":260,\"note\":\"Above the requested amount.\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_APPROVED_AMOUNT"));
        decide(app, "{\"outcome\":\"MAYBE\",\"note\":\"Not a valid outcome here.\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_OUTCOME"));
        decide(app, "{\"outcome\":\"REJECTED\",\"approvedAmount\":10,\"note\":\"Rejected with an amount.\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_APPROVED_AMOUNT"));
        assertThat(auditCount("ADMIN_REVIEW_DECIDED", String.valueOf(app))).isZero();

        decide(app, "{\"outcome\":\"APPROVED\",\"approvedAmount\":200,\"note\":\"Checked the history; a smaller amount is fine.\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.decision.reviewerEmail").value("admin@demo.rocket.local"));
        decide(app, "{\"outcome\":\"REJECTED\",\"note\":\"Trying to decide a second time.\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALREADY_REVIEWED"));
        assertThat(auditCount("ADMIN_REVIEW_DECIDED", String.valueOf(app))).isEqualTo(1);

        // the automated decision is untouched; the human decision is shown beside it
        assertThat(testJdbc.queryForMap("SELECT decision_status, score, possible_amount, reasons::text, factors::text FROM credit_applications WHERE id=?", app))
                .isEqualTo(aiBefore);
        mvc.perform(get("/api/applications/" + app).session(customer))
                .andExpect(jsonPath("$.decisionStatus").value("REVIEW")).andExpect(jsonPath("$.score").value(0.521))
                .andExpect(jsonPath("$.reviewState").value("APPROVED")).andExpect(jsonPath("$.reviewApprovedAmount").value(200.0))
                .andExpect(jsonPath("$.reviewNote").value("Checked the history; a smaller amount is fine."))
                .andExpect(jsonPath("$.canAccept").value(true));
        mvc.perform(get("/api/applications").session(customer)).andExpect(jsonPath("$[0].reviewState").value("APPROVED"));

        String accepted = mvc.perform(postJson("/api/applications/" + app + "/accept", "{}", fetchCsrf()).session(customer)
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long loan = ((Number) JsonPath.read(accepted, "$.loanId")).longValue();
        var row = testJdbc.queryForMap("SELECT principal, term_unit, installment_count FROM loans WHERE id=?", loan);
        assertThat((BigDecimal) row.get("principal")).isEqualByComparingTo("200");
        assertThat(row.get("term_unit")).isEqualTo("MONTH");
        assertThat(row.get("installment_count")).isEqualTo(6);
        assertThat(testJdbc.queryForObject("SELECT sum(principal) FROM repayment_installments WHERE loan_id=?", BigDecimal.class, loan)).isEqualByComparingTo("200");
        assertThat(testJdbc.queryForObject("SELECT amount FROM transactions WHERE loan_id=?", BigDecimal.class, loan)).isEqualByComparingTo("200");
        mvc.perform(get("/api/admin/reviews/" + app).session(admin)).andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void rejectedReviewCannotBeAccepted() throws Exception {
        MockHttpSession customer = registerAndLogin(uniqueEmail());
        long app = reviewApplication(customer);
        decide(app, "{\"outcome\":\"REJECTED\",\"note\":\"Recent late payment and an incomplete profile.\"}").andExpect(status().isOk());
        mvc.perform(get("/api/applications/" + app).session(customer))
                .andExpect(jsonPath("$.decisionStatus").value("REVIEW")).andExpect(jsonPath("$.reviewState").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Recent late payment and an incomplete profile."))
                .andExpect(jsonPath("$.reviewApprovedAmount").doesNotExist()).andExpect(jsonPath("$.canAccept").value(false));
        mvc.perform(postJson("/api/applications/" + app + "/accept", "{}", fetchCsrf()).session(customer)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("NOT_ACTIVATABLE"));
        String decided = mvc.perform(get("/api/admin/reviews?state=DECIDED&size=100").session(admin)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(decided, "$.items[*].applicationId")).contains((int) app);
    }

    @Test
    void onlyReviewApplicationsCanBeReviewed() throws Exception {
        MockHttpSession customer = registerAndLogin(uniqueEmail());
        when(analysis.score(any())).thenReturn(new AnalysisDecision("APPROVED", new BigDecimal("0.95"), new BigDecimal("1500"), "USD",
                List.of(), Map.of(), "rules-v4", false, "NOT_REQUESTED", null, Map.of()));
        String quote = mvc.perform(postJson("/api/credit-quotes", "{\"partnerSlug\":\"markethub\",\"requestedAmount\":100,\"termUnit\":\"MONTH\",\"installmentCount\":3}",
                fetchCsrf()).session(customer)).andReturn().getResponse().getContentAsString();
        String app = mvc.perform(postJson("/api/applications", "{\"partnerSlug\":\"markethub\",\"requestedAmount\":100,\"useAi\":false,\"quoteId\":%d}"
                        .formatted(((Number) JsonPath.read(quote, "$.id")).longValue()), fetchCsrf()).session(customer)
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(jsonPath("$.reviewState").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(app, "$.id")).longValue();
        mvc.perform(get("/api/admin/reviews/" + id).session(admin)).andExpect(status().isNotFound());
        decide(id, "{\"outcome\":\"REJECTED\",\"note\":\"Not a review application.\"}").andExpect(status().isNotFound());
    }

    // ---- partners and audit ------------------------------------------------------------------------

    @Test
    void everyAdminWriteIsAuditedOnceAndTheLogIsReadOnly() throws Exception {
        long partner = createPartner("400");
        mvc.perform(withCsrf(put("/api/admin/partners/" + partner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Renamed store\",\"amountCap\":450,\"active\":true}"), fetchCsrf()).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Renamed store"));
        mvc.perform(withCsrf(put("/api/admin/partners/" + partner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Renamed store\",\"amountCap\":0,\"active\":true}"), fetchCsrf()).session(admin))
                .andExpect(status().isBadRequest());
        assertThat(auditCount("ADMIN_PARTNER_UPDATED", String.valueOf(partner))).isEqualTo(1);
        assertThat(testJdbc.queryForObject("SELECT details::text FROM audit_events WHERE action='ADMIN_PARTNER_UPDATED' AND target_id=?",
                String.class, String.valueOf(partner))).contains("\"amountCap\"").contains("450.00");

        String email = uniqueEmail();
        register(email, "password-123");
        long user = id(email);
        mvc.perform(postJson("/api/admin/users/" + user + "/disable", "{}", fetchCsrf()).session(admin)).andExpect(status().isOk());
        mvc.perform(postJson("/api/admin/users/" + user + "/disable", "{}", fetchCsrf()).session(admin)).andExpect(status().isOk()); // no change, no row
        mvc.perform(postJson("/api/admin/users/" + user + "/enable", "{}", fetchCsrf()).session(admin)).andExpect(status().isOk());
        assertThat(auditCount("ADMIN_USER_DISABLED", String.valueOf(user))).isEqualTo(1);
        assertThat(auditCount("ADMIN_USER_ENABLED", String.valueOf(user))).isEqualTo(1);

        String log = mvc.perform(get("/api/admin/audit?actor=admin@demo&size=5").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("ADMIN_USER_ENABLED"))
                .andExpect(jsonPath("$.items[1].action").value("ADMIN_USER_DISABLED"))
                .andExpect(jsonPath("$.items[0].actorRole").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(log).doesNotContain("$2").doesNotContainIgnoringCase("password_hash");
        mvc.perform(get("/api/admin/audit?action=ADMIN_PARTNER_UPDATED").session(admin)).andExpect(jsonPath("$.items[0].targetId").value(String.valueOf(partner)));

        // no endpoint changes the log, and the database refuses it too
        mvc.perform(withCsrf(delete("/api/admin/audit"), fetchCsrf()).session(admin)).andExpect(status().isMethodNotAllowed());
        mvc.perform(withCsrf(put("/api/admin/audit").contentType(MediaType.APPLICATION_JSON).content("{}"), fetchCsrf()).session(admin))
                .andExpect(status().isMethodNotAllowed());
        assertThatThrownBy(() -> testJdbc.update("UPDATE audit_events SET action='X' WHERE target_id=?", String.valueOf(user)))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> testJdbc.update("DELETE FROM audit_events WHERE target_id=?", String.valueOf(user)))
                .hasStackTraceContaining("append-only");
    }

    @Test
    void adminResponsesCarryNoCustomerFinanceOrSecrets() throws Exception {
        for (String path : new String[] {"/api/admin/overview", "/api/admin/users?size=100", "/api/admin/partners", "/api/admin/audit",
                "/api/admin/reviews?state=OPEN", "/api/admin/reviews?state=DECIDED"}) {
            String json = mvc.perform(get(path).session(admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(json).as(path).doesNotContain("$2a$").doesNotContain("$2b$").doesNotContainIgnoringCase("passwordHash")
                    .doesNotContainIgnoringCase("monthlyNetIncome").doesNotContain("featureSnapshot").doesNotContain("balance")
                    .doesNotContain("\"address\"");
        }
        when(collector.health()).thenReturn("rocket-profile-v1");
        mvc.perform(get("/api/admin/overview").session(admin))
                .andExpect(jsonPath("$.usersByRole.ADMIN.enabled").isNumber())
                .andExpect(jsonPath("$.services.backend").value("UP"))
                .andExpect(jsonPath("$.services.database").value("UP"))
                .andExpect(jsonPath("$.services.collector").value("UP"));
        when(collector.health()).thenThrow(new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "COLLECTOR_UNAVAILABLE", "down"));
        mvc.perform(get("/api/admin/overview").session(admin)).andExpect(jsonPath("$.services.collector").value("DOWN"));
    }
}
