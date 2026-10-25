package com.rocketcredit.backend.signals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rocketcredit.backend.AbstractIntegrationTest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

/** The Facebook OAuth round trip with Graph mocked: state check, declined permissions, expired session. */
@TestPropertySource(properties = {"rocket.social.meta-app-id=test-app", "rocket.social.meta-app-secret=test-secret"})
class SocialConnectionFlowTest extends AbstractIntegrationTest {
    private static final String CALLBACK = "/api/me/social-connection/facebook/callback";
    private static final String BACK = "/account-dashboard?section=activity&social=";

    @MockBean FacebookClient facebook;

    private String start(MockHttpSession session) throws Exception {
        when(facebook.authorizeUrl(anyString())).thenAnswer(inv -> "https://facebook.test/dialog?state=" + inv.getArgument(0));
        String body = mvc.perform(withCsrf(post("/api/me/social-connection/facebook/start"), fetchCsrf()).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.authorizeUrl");
        return url.substring(url.indexOf("state=") + 6);
    }

    @Test
    void expiredSessionIsSentBackInsteadOfA401() throws Exception {
        mvc.perform(get(CALLBACK).param("code", "c").param("state", "s"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", BACK + "session"));
    }

    @Test
    void forgedStateIsRejected() throws Exception {
        var session = registerAndLogin(uniqueEmail());
        start(session);
        mvc.perform(get(CALLBACK).session(session).param("code", "c").param("state", "forged"))
                .andExpect(header().string("Location", BACK + "error"));
    }

    @Test
    void declinedPostsPermissionIsNotStoredAndAGrantedOneIs() throws Exception {
        var session = registerAndLogin(uniqueEmail());
        when(facebook.exchangeCode("code-1")).thenReturn(new FacebookClient.TokenResult("tok-1", null));
        when(facebook.grantedPermissions("tok-1")).thenReturn(Set.of("public_profile"));

        String state = start(session);
        mvc.perform(get(CALLBACK).session(session).param("code", "code-1").param("state", state))
                .andExpect(header().string("Location", BACK + "permission"));
        verify(facebook).revoke("tok-1");
        mvc.perform(get("/api/me/social-connection").session(session)).andExpect(jsonPath("$.connected").value(false));

        when(facebook.exchangeCode("code-2")).thenReturn(new FacebookClient.TokenResult("tok-2", null));
        when(facebook.grantedPermissions("tok-2")).thenReturn(Set.of("public_profile", "user_posts"));
        when(facebook.me("tok-2")).thenReturn(new FacebookClient.Profile("fb-" + System.nanoTime(), "Ada Lovelace"));

        state = start(session);
        mvc.perform(get(CALLBACK).session(session).param("code", "code-2").param("state", state))
                .andExpect(header().string("Location", BACK + "connected"));
        mvc.perform(get("/api/me/social-connection").session(session))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));

        // The stored token is encrypted, never the raw value.
        assertThat(mvc.perform(get("/api/me/social-connection").session(session)).andReturn().getResponse().getContentAsString())
                .doesNotContain("tok-2");

        // Permission withdrawn later on facebook.com: the live run asks for a reconnect instead of a generic 503.
        when(facebook.fetchPosts("tok-2")).thenThrow(new FacebookClient.PermissionMissingException());
        mvc.perform(postJson("/api/me/demo-signals/social-runs", "{\"scenarioId\":\"facebook-live\"}", fetchCsrf()).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SOCIAL_PERMISSION_REQUIRED"));
    }
}
