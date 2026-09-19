package com.rocketcredit.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the full application against a throwaway PostgreSQL 16; Flyway runs the real schema.
 * One container serves every test class so the cached Spring context keeps a live database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start(); // singleton for the whole test JVM; Testcontainers' reaper removes it afterwards
    }

    @Autowired protected MockMvc mvc;

    /** The React client's CSRF material: XSRF-TOKEN cookie plus its value for the X-XSRF-TOKEN header. */
    protected record Csrf(Cookie cookie, String headerName, String token) {}

    protected Csrf fetchCsrf() throws Exception {
        var result = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        String body = result.getResponse().getContentAsString();
        return new Csrf(cookie, JsonPath.read(body, "$.headerName"), JsonPath.read(body, "$.token"));
    }

    protected MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder, Csrf csrf) {
        return builder.cookie(csrf.cookie()).header(csrf.headerName(), csrf.token());
    }

    protected MockHttpServletRequestBuilder postJson(String path, String body, Csrf csrf) {
        return withCsrf(post(path).contentType(MediaType.APPLICATION_JSON).content(body), csrf);
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    protected static String registerJson(String email, String password, String name) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"%s\"}".formatted(email, password, name);
    }

    protected static String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    protected void register(String email, String password) throws Exception {
        mvc.perform(postJson("/api/auth/register", registerJson(email, password, "Test User"), fetchCsrf()))
                .andExpect(status().isCreated());
    }

    protected MockHttpSession login(String email, String password) throws Exception {
        var result = mvc.perform(postJson("/api/auth/login", loginJson(email, password), fetchCsrf()))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected MockHttpSession registerAndLogin(String email) throws Exception {
        register(email, "password-123");
        return login(email, "password-123");
    }
}
