// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.auth.AuthenticatedUser;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one audit_events row. It must run inside the caller's transaction (MANDATORY), so the change
 * and its audit row are committed or rolled back together. The table is append-only (a database
 * trigger refuses UPDATE and DELETE). Callers pass only identifiers, amounts, flags and names in
 * {@code details}: never passwords, hashes, tokens or free text about a customer.
 */
@Component
public class AuditLog {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditLog(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuthenticatedUser actor, String action, String targetType, Object targetId, Map<String, ?> details) {
        try {
            jdbc.update("""
                    INSERT INTO audit_events(actor_user_id, actor_role, action, target_type, target_id, details)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb)""",
                    actor.getId(), actor.getRole().name(), action, targetType,
                    targetId == null ? null : String.valueOf(targetId), mapper.writeValueAsString(details));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
