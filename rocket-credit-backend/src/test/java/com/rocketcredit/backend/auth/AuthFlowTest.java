package com.rocketcredit.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AuthFlowTest extends AbstractIntegrationTest {

    @Autowired UserRepository users;

    // ---- 2.1 accounts ------------------------------------------------------------------------

    @Test
    void registrationStoresBcryptHashAndNeverReturnsIt() throws Exception {
        String email = uniqueEmail();
        String body = mvc.perform(postJson("/api/auth/register", registerJson(email, "correct horse battery", "Ada"), fetchCsrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Ada"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("password").doesNotContain("$2");
        var stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo("correct horse battery");
    }

    @Test
    void emailIsNormalizedAndUnique() throws Exception {
        String email = uniqueEmail();
        register(email.toUpperCase(), "password-123");
        assertThat(users.findByEmail(email)).isPresent();                    // stored lower-case

        mvc.perform(postJson("/api/auth/register", registerJson(email, "another-pass-1", "Dup"), fetchCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));

        login(email, "password-123");                                          // either spelling logs in
        login(email.toUpperCase(), "password-123");
    }

    @Test
    void registrationValidatesInputWithoutEchoingValues() throws Exception {
        mvc.perform(postJson("/api/auth/register", registerJson("not-an-email", "short", ""), fetchCsrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists())
                .andExpect(jsonPath("$.fields.displayName").exists())
                .andExpect(content().string(not(containsString("short"))));
    }

    // ---- 2.2 sessions ------------------------------------------------------------------------

    @Test
    void registerLoginProtectedRequestLogout() throws Exception {
        String email = uniqueEmail();
        register(email, "password-123");

        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());          // no session

        MockHttpSession session = login(email, "password-123");
        assertThat(session).isNotNull();

        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(withCsrf(post("/api/auth/logout"), fetchCsrf()).session(session))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();

        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());          // anonymous again
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameGenericAnswer() throws Exception {
        String email = uniqueEmail();
        register(email, "password-123");

        String wrong = mvc.perform(postJson("/api/auth/login", loginJson(email, "nope-nope-nope"), fetchCsrf()))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(postJson("/api/auth/login", loginJson(uniqueEmail(), "password-123"), fetchCsrf()))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrong).isEqualTo(unknown).contains("INVALID_CREDENTIALS");
    }

    @Test
    void loginRotatesThePreLoginSessionId() throws Exception {
        String email = uniqueEmail();
        register(email, "password-123");

        // An anonymous visitor already holds a session before logging in
        var pre = new MockHttpSession();
        String before = pre.getId();

        var after = mvc.perform(postJson("/api/auth/login", loginJson(email, "password-123"), fetchCsrf()).session(pre))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        assertThat(after.getId()).isNotEqualTo(before);
        mvc.perform(get("/api/me").session((MockHttpSession) after)).andExpect(status().isOk());
    }

    @Test
    void invalidatedSessionIsRejected() throws Exception {
        String email = uniqueEmail();
        MockHttpSession session = registerAndLogin(email);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());

        session.invalidate();
        // The browser now has a cookie for a session the server no longer knows -> anonymous -> 401
        mvc.perform(get("/api/me").session(new MockHttpSession())).andExpect(status().isUnauthorized());
    }

    // ---- 2.3 CSRF ----------------------------------------------------------------------------

    @Test
    void csrfEndpointIssuesReadableCookieMatchingTheToken() throws Exception {
        Csrf csrf = fetchCsrf();
        assertThat(csrf.cookie()).isNotNull();
        assertThat(csrf.cookie().isHttpOnly()).isFalse();        // JS must read it
        assertThat(csrf.headerName()).isEqualTo("X-XSRF-TOKEN");
        assertThat(csrf.token()).isEqualTo(csrf.cookie().getValue());
    }

    @Test
    void stateChangingRequestsNeedCookieAndMatchingHeader() throws Exception {
        Csrf csrf = fetchCsrf();
        String body = registerJson(uniqueEmail(), "password-123", "X");

        // neither cookie nor header
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // cookie only
        mvc.perform(post("/api/auth/register").cookie(csrf.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // header that does not match the cookie
        mvc.perform(post("/api/auth/register").cookie(csrf.cookie()).header(csrf.headerName(), "forged")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // login and logout are protected too
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("a@b.test", "password-123")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());

        // the correct pair is accepted
        mvc.perform(postJson("/api/auth/register", body, csrf)).andExpect(status().isCreated());
    }

    @Test
    void privateRoutesAreClosedWithoutASession() throws Exception {
        mvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/anything-else")).andExpect(status().isUnauthorized());
    }
}
