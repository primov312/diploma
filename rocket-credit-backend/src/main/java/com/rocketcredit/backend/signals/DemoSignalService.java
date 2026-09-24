package com.rocketcredit.backend.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.analysis.AnalysisUnavailableException;
import com.rocketcredit.backend.common.ApiException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DemoSignalService {
    private final JdbcTemplate jdbc;
    private final AnalysisClient analysis;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public DemoSignalService(JdbcTemplate jdbc, AnalysisClient analysis, ObjectMapper mapper, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.analysis = analysis;
        this.mapper = mapper;
        this.tx = tx;
    }

    public DemoSignalDtos.Settings settings(long userId) {
        return tx.execute(status -> {
            ensure(userId);
            return settingsLocked(userId);
        });
    }

    public DemoSignalDtos.Settings update(long userId, DemoSignalDtos.UpdateRequest request) {
        return tx.execute(status -> {
            ensure(userId);
            var before = settingsLocked(userId);
            jdbc.update("""
                    UPDATE demo_signal_settings SET location_enabled=?, social_enabled=?,
                      permission_generation=permission_generation + CASE WHEN location_enabled<>? OR social_enabled<>? THEN 1 ELSE 0 END,
                      updated_at=now() WHERE user_id=?
                    """, request.locationEnabled(), request.socialEnabled(), request.locationEnabled(), request.socialEnabled(), userId);
            if (before.locationEnabled() && !request.locationEnabled()) cancel(userId, "LOCATION");
            if (before.socialEnabled() && !request.socialEnabled()) cancel(userId, "SOCIAL");
            return settingsLocked(userId);
        });
    }

    public DemoSignalDtos.RunAccepted run(long userId, String kind, String scenarioId) {
        RunJob job = tx.execute(status -> {
            ensure(userId);
            var settings = settingsLocked(userId);
            boolean enabled = "LOCATION".equals(kind) ? settings.locationEnabled() : settings.socialEnabled();
            if (!enabled) throw ApiException.badRequest("OPTIONAL_ANALYSIS_DISABLED", "Enable permission for this analysis before running it.");
            String key = kind + ":" + scenarioId + ":" + settings.permissionGeneration();
            List<RunJob> existing = jdbc.query("""
                    SELECT id, permission_generation FROM analysis_jobs
                    WHERE user_id=? AND deduplication_key=?
                    """, (rs, row) -> new RunJob(rs.getLong("id"), rs.getLong("permission_generation"), true), userId, key);
            if (!existing.isEmpty()) return existing.getFirst();
            Long id = jdbc.queryForObject("""
                    INSERT INTO analysis_jobs (user_id, kind, source_revision, permission_generation,
                        state, attempt_count, scenario_id, deduplication_key)
                    VALUES (?, ?, 0, ?, 'RUNNING', 1, ?, ?) RETURNING id
                    """, Long.class, userId, kind, settings.permissionGeneration(), scenarioId, key);
            return new RunJob(id, settings.permissionGeneration(), false);
        });
        if (job.existing()) return existingResult(userId, job.id());

        try {
            String declaredDistrict = jdbc.query("""
                    SELECT d.display_name FROM user_address_state s
                    JOIN user_address_revisions a ON a.user_id=s.user_id AND a.revision=s.current_revision
                    JOIN demo_districts d ON d.district_id=a.district_id WHERE s.user_id=?
                    """, rs -> rs.next() ? rs.getString(1) : null, userId);
            JsonNode report = analysis.runDemoAnalysis(kind, scenarioId, declaredDistrict);
            return tx.execute(status -> {
                var settings = settingsLocked(userId);
                boolean enabled = "LOCATION".equals(kind) ? settings.locationEnabled() : settings.socialEnabled();
                if (!enabled || settings.permissionGeneration() != job.permissionGeneration()) {
                    jdbc.update("UPDATE analysis_jobs SET state='CANCELLED', completed_at=now() WHERE id=?", job.id());
                    return new DemoSignalDtos.RunAccepted(job.id(), "CANCELLED", null);
                }
                jdbc.update("""
                        INSERT INTO demo_signal_reports (user_id, job_id, kind, scenario_id, permission_generation,
                            report, data_source, analysis_mode)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, 'SYNTHETIC', ?)
                        """, userId, job.id(), kind, scenarioId, job.permissionGeneration(), report.toString(),
                        report.path("analysisMode").asText("FIXTURE"));
                jdbc.update("UPDATE analysis_jobs SET state='SUCCEEDED', completed_at=now() WHERE id=?", job.id());
                return new DemoSignalDtos.RunAccepted(job.id(), "SUCCEEDED", report);
            });
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            tx.executeWithoutResult(status -> jdbc.update("UPDATE analysis_jobs SET state='FAILED', failure_code='PROVIDER_UNAVAILABLE', completed_at=now() WHERE id=?", job.id()));
            if (e instanceof AnalysisUnavailableException unavailable) throw unavailable;
            throw new AnalysisUnavailableException("demo analysis failed", e);
        }
    }

    public List<JsonNode> reports(long userId, String kind) {
        return jdbc.query("""
                SELECT report::text FROM demo_signal_reports WHERE user_id=? AND kind=?
                ORDER BY created_at DESC LIMIT 20
                """, (rs, row) -> readReport(rs.getString(1)), userId, kind);
    }

    private DemoSignalDtos.RunAccepted existingResult(long userId, long jobId) {
        List<JsonNode> reports = jdbc.query("SELECT report::text FROM demo_signal_reports WHERE user_id=? AND job_id=?",
                (rs, row) -> readReport(rs.getString(1)), userId, jobId);
        String state = jdbc.queryForObject("SELECT state FROM analysis_jobs WHERE user_id=? AND id=?", String.class, userId, jobId);
        return new DemoSignalDtos.RunAccepted(jobId, state, reports.isEmpty() ? null : reports.getFirst());
    }

    private void ensure(long userId) {
        jdbc.update("INSERT INTO demo_signal_settings (user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING", userId);
    }

    private JsonNode readReport(String json) {
        try { return mapper.readTree(json); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private DemoSignalDtos.Settings settingsLocked(long userId) {
        return jdbc.queryForObject("SELECT location_enabled, social_enabled, permission_generation FROM demo_signal_settings WHERE user_id=? FOR UPDATE",
                (rs, row) -> new DemoSignalDtos.Settings(rs.getBoolean(1), rs.getBoolean(2), rs.getLong(3)), userId);
    }

    private void cancel(long userId, String kind) {
        jdbc.update("UPDATE analysis_jobs SET state='CANCELLED', completed_at=now() WHERE user_id=? AND kind=? AND state IN ('QUEUED','RUNNING')",
                userId, kind);
    }

    private record RunJob(long id, long permissionGeneration, boolean existing) {}
}
