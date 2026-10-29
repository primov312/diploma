// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rocketcredit.backend.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** The three kinds of users: what login returns, who may call which area, and account changes mid-session. */
class RoleAccessTest extends AbstractIntegrationTest {

    @Test
    void loginAndMeReturnTheRoleAndTheSellersPartner() throws Exception {
        mvc.perform(postJson("/api/auth/login", loginJson("avery@demo.rocket.local", DEMO_PASSWORD), fetchCsrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.partnerSlug").doesNotExist());
        mvc.perform(postJson("/api/auth/login", loginJson("netflix@demo.rocket.local", DEMO_PASSWORD), fetchCsrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SELLER"))
                .andExpect(jsonPath("$.partnerSlug").value("streambox")).andExpect(jsonPath("$.partnerName").value("Netflix"));
        mvc.perform(postJson("/api/auth/login", loginJson("admin@demo.rocket.local", DEMO_PASSWORD), fetchCsrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.displayName").value("Demo Administrator"));
        mvc.perform(get("/api/me").session(loginDemo("zara")))
                .andExpect(jsonPath("$.role").value("SELLER")).andExpect(jsonPath("$.partnerSlug").value("threadly"));
        mvc.perform(get("/api/me").session(loginDemo("amazon"))).andExpect(jsonPath("$.partnerSlug").value("markethub"));
    }

    @Test
    void registrationIgnoresRolePartnerAndEnabledProperties() throws Exception {
        String email = uniqueEmail();
        long partner = createPartner("500");
        String body = "{\"email\":\"%s\",\"password\":\"password-123\",\"displayName\":\"Eve\",\"role\":\"ADMIN\",\"partnerId\":%d,\"enabled\":false}"
                .formatted(email, partner);
        mvc.perform(postJson("/api/auth/register", body, fetchCsrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CUSTOMER"));
        assertThat(testJdbc.queryForObject("SELECT role FROM users WHERE email=?", String.class, email)).isEqualTo("CUSTOMER");
        assertThat(testJdbc.queryForObject("SELECT count(*) FROM seller_accounts s JOIN users u ON u.id=s.user_id WHERE u.email=?",
                Long.class, email)).isZero();
        MockHttpSession eve = login(email, "password-123");
        mvc.perform(get("/api/admin/overview").session(eve)).andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountCannotSignInAndGetsTheSameAnswerAsAWrongPassword() throws Exception {
        String email = uniqueEmail();
        register(email, "password-123");
        testJdbc.update("UPDATE users SET enabled = false WHERE email = ?", email);
        String disabled = mvc.perform(postJson("/api/auth/login", loginJson(email, "password-123"), fetchCsrf()))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String wrong = mvc.perform(postJson("/api/auth/login", loginJson("avery@demo.rocket.local", "wrong-password"), fetchCsrf()))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(disabled).isEqualTo(wrong).contains("INVALID_CREDENTIALS");
    }

    @Test
    void sellerDisabledMidSessionIsSignedOutOnTheNextRequest() throws Exception {
        MockHttpSession admin = loginDemo("admin");
        long partner = createPartner("900");
        String email = uniqueEmail();
        MockHttpSession seller = createSeller(admin, partner, email);
        mvc.perform(get("/api/seller/overview").session(seller)).andExpect(status().isOk());

        long id = testJdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        mvc.perform(postJson("/api/admin/users/" + id + "/disable", "{}", fetchCsrf()).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/seller/overview").session(seller)).andExpect(status().isUnauthorized());
        assertThat(seller.isInvalid()).isTrue();
        mvc.perform(postJson("/api/auth/login", loginJson(email, "seller-pass-1"), fetchCsrf())).andExpect(status().isUnauthorized());

        // enabled again: a new login works
        mvc.perform(postJson("/api/admin/users/" + id + "/enable", "{}", fetchCsrf()).session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/seller/overview").session(login(email, "seller-pass-1"))).andExpect(status().isOk());
    }

    /**
     * One representative endpoint per area, called as every kind of user. 0 = anonymous.
     * Expected status per caller: anonymous, customer, seller, admin.
     */
    @Test
    void permissionMatrix() throws Exception {
        Map<String, MockHttpSession> callers = new LinkedHashMap<>();
        callers.put("anonymous", null);
        callers.put("customer", registerAndLogin(uniqueEmail()));
        callers.put("seller", loginDemo("netflix"));
        callers.put("admin", loginDemo("admin"));

        Map<String, int[]> matrix = new LinkedHashMap<>();
        matrix.put("/api/catalog", new int[] {200, 200, 200, 200});
        matrix.put("/api/partners", new int[] {200, 200, 200, 200});
        matrix.put("/api/me", new int[] {401, 200, 200, 200});
        matrix.put("/api/wallet", new int[] {401, 200, 403, 403});
        matrix.put("/api/applications", new int[] {401, 200, 403, 403});
        matrix.put("/api/me/profile", new int[] {401, 200, 403, 403});
        matrix.put("/api/me/consents", new int[] {401, 200, 403, 403});
        matrix.put("/api/history", new int[] {401, 200, 403, 403});
        matrix.put("/api/seller/overview", new int[] {401, 403, 200, 403});
        matrix.put("/api/seller/orders", new int[] {401, 403, 200, 403});
        matrix.put("/api/admin/overview", new int[] {401, 403, 403, 200});
        matrix.put("/api/admin/users", new int[] {401, 403, 403, 200});
        matrix.put("/api/somewhere-new", new int[] {401, 403, 403, 403}); // deny by default

        for (var row : matrix.entrySet()) {
            int i = 0;
            for (var caller : callers.entrySet()) {
                var request = get(row.getKey());
                if (caller.getValue() != null) request.session(caller.getValue());
                int expected = row.getValue()[i++];
                var result = mvc.perform(request).andReturn().getResponse();
                assertThat(result.getStatus()).as("%s as %s", row.getKey(), caller.getKey()).isEqualTo(expected);
                if (expected == 403) assertThat(result.getContentAsString()).contains("\"error\":\"FORBIDDEN_ROLE\"");
            }
        }

        // writes: a seller or an administrator cannot apply, buy or top up; a customer cannot reach staff writes
        for (String who : new String[] {"seller", "admin"}) {
            mvc.perform(postJson("/api/applications", "{\"partnerSlug\":\"markethub\",\"requestedAmount\":10}", fetchCsrf())
                            .session(callers.get(who)).header("Idempotency-Key", "k-" + who))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN_ROLE"));
            mvc.perform(postJson("/api/wallet/top-ups", "{\"amount\":10}", fetchCsrf()).session(callers.get(who)).header("Idempotency-Key", "t-" + who))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN_ROLE"));
        }
        mvc.perform(postJson("/api/seller/products", "{}", fetchCsrf()).session(callers.get("customer")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN_ROLE"));
        mvc.perform(postJson("/api/admin/users/1/disable", "{}", fetchCsrf()).session(callers.get("seller")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN_ROLE"));
        // a missing CSRF token is still refused first, with its own code
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/users/1/disable")
                        .session(callers.get("admin")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("CSRF_INVALID"));
    }

    @Test
    void staffAccountsHaveNoCustomerData() {
        for (String who : new String[] {"admin", "netflix", "zara", "amazon"}) {
            long id = testJdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, who + "@demo.rocket.local");
            for (String table : new String[] {"wallet_accounts", "demo_financial_profiles", "consent_records", "financial_input_state"}) {
                assertThat(testJdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?", Long.class, id))
                        .as(table + " for " + who).isZero();
            }
        }
    }

    @Test
    void theDatabaseKeepsSellerLinksConsistent() {
        long partner = createPartner("100");
        // a seller without partner, or a customer with one, is refused at commit
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(txManager)
                .executeWithoutResult(s -> testJdbc.update("INSERT INTO users(email,password_hash,display_name,role) VALUES (?, 'x', 'S', 'SELLER')", uniqueEmail())))
                .hasStackTraceContaining("has no partner");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(txManager)
                .executeWithoutResult(s -> {
                    long user = testJdbc.queryForObject("INSERT INTO users(email,password_hash,display_name) VALUES (?, 'x', 'C') RETURNING id", Long.class, uniqueEmail());
                    testJdbc.update("INSERT INTO seller_accounts(user_id, partner_id) VALUES (?, ?)", user, partner);
                }))
                .hasStackTraceContaining("not a seller");
    }

    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager txManager;
}
