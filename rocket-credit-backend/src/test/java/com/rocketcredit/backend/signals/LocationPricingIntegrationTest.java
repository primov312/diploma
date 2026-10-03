package com.rocketcredit.backend.signals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.analysis.AnalysisClient;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

class LocationPricingIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoSignalService signals;
    @Autowired ObjectMapper mapper;
    @MockBean AnalysisClient analysis;

    private String dataset(int cafePrice) {
        String version = "test-" + UUID.randomUUID();
        String checksum = UUID.randomUUID().toString().replace("-", "").repeat(2);
        jdbc.update("""
                INSERT INTO location_price_datasets
                (dataset_version,housing_observed_at,housing_retrieved_at,district_average,content_checksum,source_url)
                VALUES (?, '2026-09-30', now(), 1000, ?, 'https://example.test/housing')
                """, version, checksum);
        jdbc.update("""
                INSERT INTO location_district_prices
                SELECT ?, 'budapest-' || r, n, 'Budapest District ' || upper(r), 1000, 10, 1, 'https://example.test/housing'
                FROM unnest(ARRAY['i','ii','iii','iv','v','vi','vii','viii','ix','x','xi','xii','xiii','xiv','xv','xvi','xvii','xviii','xix','xx','xxi','xxii','xxiii'])
                WITH ORDINALITY AS districts(r,n)
                """, version);
        for (String category : new String[]{"GROCERY", "LIBRARY", "GYM", "CAFE", "STARBUCKS"}) {
            String unit = switch (category) { case "GROCERY" -> "BASKET"; case "LIBRARY" -> "YEAR"; case "GYM" -> "ENTRY"; default -> "DRINK"; };
            jdbc.update("""
                    INSERT INTO location_venue_baselines
                    (dataset_version,category,benchmark_description,unit,baseline_price,source_url,retrieved_at,evidence)
                    VALUES (?,?,?,?,?,'https://example.test/benchmark',now(),'{"channel":"test"}'::jsonb)
                    """, version, category, "Test benchmark", unit, cafePrice);
        }
        jdbc.update("""
                INSERT INTO location_venue_prices
                SELECT d.dataset_version, d.district_id, b.category, b.baseline_price
                FROM location_district_prices d JOIN location_venue_baselines b USING (dataset_version)
                WHERE d.dataset_version=?
                """, version);
        return version;
    }

    private void activate(String version) {
        jdbc.update("UPDATE location_price_datasets SET active=FALSE WHERE active");
        if (version != null) jdbc.update("UPDATE location_price_datasets SET active=TRUE WHERE dataset_version=?", version);
    }

    @Test
    void refreshDuringAnalysisKeepsCapturedVersionAndNewRunUsesNewVersion() throws Exception {
        String oldVersion = dataset(1490), newVersion = dataset(1590);
        activate(oldVersion);
        String email = uniqueEmail(); register(email, "password-123");
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        signals.update(userId, new DemoSignalDtos.UpdateRequest(true, false));
        var raw = mapper.readTree("""
                {"status":"COMPLETE","dataSource":"SYNTHETIC","scenarioId":"sparse","visits":[
                 {"id":"visit","district":"Budapest XI","place":"Cafe"}]}
                """);
        AtomicBoolean first = new AtomicBoolean(true);
        when(analysis.runDemoAnalysis(eq("LOCATION"), eq("sparse"), nullable(String.class))).thenAnswer(invocation -> {
            if (first.getAndSet(false)) activate(newVersion);
            return raw;
        });
        var oldRun = signals.run(userId, "LOCATION", "sparse");
        assertThat(oldRun.report().at("/pricing/datasetVersion").asText()).isEqualTo(oldVersion);
        assertThat(oldRun.report().at("/visits/0/pricing/estimatedPrice").decimalValue()).isEqualByComparingTo("1490");
        var newRun = signals.run(userId, "LOCATION", "sparse");
        assertThat(newRun.jobId()).isNotEqualTo(oldRun.jobId());
        assertThat(newRun.report().at("/pricing/datasetVersion").asText()).isEqualTo(newVersion);
        assertThat(newRun.report().at("/visits/0/pricing/estimatedPrice").decimalValue()).isEqualByComparingTo("1590");
        var duplicate = signals.run(userId, "LOCATION", "sparse");
        assertThat(duplicate.jobId()).isEqualTo(newRun.jobId());
        assertThat(signals.reports(userId, "LOCATION").get(1).at("/visits/0/pricing/estimatedPrice").decimalValue()).isEqualByComparingTo("1490");
        verify(analysis, times(2)).runDemoAnalysis(eq("LOCATION"), eq("sparse"), nullable(String.class));
        verify(analysis, never()).score(any());
        activate(null);
    }

    @Test
    void missingDatasetDoesNotFailAnalysisOrChangeFinancialRevisions() throws Exception {
        activate(null);
        String email = uniqueEmail(); register(email, "password-123");
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        var before = jdbc.queryForList("SELECT * FROM financial_input_revisions WHERE user_id=?", userId);
        signals.update(userId, new DemoSignalDtos.UpdateRequest(true, false));
        when(analysis.runDemoAnalysis(eq("LOCATION"), eq("sparse"), nullable(String.class))).thenReturn(mapper.readTree("""
                {"status":"COMPLETE","dataSource":"SYNTHETIC","visits":[{"district":"Budapest XI","place":"Cafe"}]}
                """));
        var result = signals.run(userId, "LOCATION", "sparse");
        assertThat(result.state()).isEqualTo("SUCCEEDED");
        assertThat(result.report().at("/visits/0/pricingStatus").asText()).isEqualTo("NO_DATASET");
        assertThat(jdbc.queryForList("SELECT * FROM financial_input_revisions WHERE user_id=?", userId)).isEqualTo(before);
        verify(analysis, never()).score(any());
    }
}
