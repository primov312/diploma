package com.rocketcredit.backend.signals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoSignalSettingsIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoSignalService signals;
    @Autowired ObjectMapper mapper;
    @MockBean AnalysisClient analysis;

    @Test
    void bothSettingsStayEnabledForNewAccountsAndLegacyDisableRequests() throws Exception {
        String email = uniqueEmail();
        var session = registerAndLogin(email);
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);

        mvc.perform(get("/api/me/demo-signals/settings").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationEnabled").value(true))
                .andExpect(jsonPath("$.socialEnabled").value(true));
        long generation = signals.settings(userId).permissionGeneration();

        mvc.perform(withCsrf(put("/api/me/demo-signals/settings").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locationEnabled\":false,\"socialEnabled\":false}"), fetchCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationEnabled").value(true))
                .andExpect(jsonPath("$.socialEnabled").value(true))
                .andExpect(jsonPath("$.permissionGeneration").value(generation));

        assertThat(jdbc.queryForObject("SELECT location_enabled AND social_enabled FROM demo_signal_settings WHERE user_id=?",
                Boolean.class, userId)).isTrue();
        assertThatThrownBy(() -> jdbc.update("UPDATE demo_signal_settings SET location_enabled=FALSE WHERE user_id=?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void socialAnalysisRunsWithoutAnEnableRequest() throws Exception {
        String email = uniqueEmail();
        register(email, "password-123");
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        when(analysis.runDemoAnalysis(eq("SOCIAL"), eq("ordinary"), nullable(String.class)))
                .thenReturn(mapper.readTree("{\"status\":\"COMPLETE\",\"dataSource\":\"SYNTHETIC\",\"analysisMode\":\"FIXTURE\"}"));

        var result = signals.run(userId, "SOCIAL", "ordinary");

        assertThat(result.state()).isEqualTo("SUCCEEDED");
        assertThat(signals.settings(userId).socialEnabled()).isTrue();
        assertThat(signals.reports(userId, "SOCIAL")).hasSize(1);
    }
}
