// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.consents;

import com.rocketcredit.backend.common.ApiException;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records, reads and enforces customer consent. The newest row for a purpose decides. A grant given under an
 * older policy version does not count: the customer has to agree again to the current wording.
 */
@Service
public class ConsentService {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public ConsentService(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    private record State(boolean granted, String policyVersion, String source, OffsetDateTime decidedAt) {
        boolean effective() { return granted && ConsentPolicy.VERSION.equals(policyVersion); }
    }

    private Map<ConsentPurpose, State> states(long userId) {
        Map<ConsentPurpose, State> result = new EnumMap<>(ConsentPurpose.class);
        jdbc.query("""
                SELECT DISTINCT ON (purpose) purpose, granted, policy_version, source, created_at
                FROM consent_records WHERE user_id=? ORDER BY purpose, id DESC
                """, rs -> {
            result.put(ConsentPurpose.valueOf(rs.getString("purpose")),
                    new State(rs.getBoolean("granted"), rs.getString("policy_version"), rs.getString("source"),
                            rs.getObject("created_at", OffsetDateTime.class)));
        }, userId);
        return result;
    }

    @Transactional(readOnly = true)
    public ConsentDtos.View view(long userId) {
        var states = states(userId);
        var items = ConsentPolicy.texts().stream().map(text -> {
            State s = states.get(text.purpose());
            return new ConsentDtos.Item(text.purpose(), text.title(), text.text(), text.requiredForCredit(),
                    s != null && s.effective(), s != null && ConsentPolicy.VERSION.equals(s.policyVersion()),
                    s == null ? null : s.source(), s == null ? null : s.decidedAt());
        }).toList();
        return new ConsentDtos.View(ConsentPolicy.VERSION, items);
    }

    public boolean has(long userId, ConsentPurpose purpose) {
        State s = states(userId).get(purpose);
        return s != null && s.effective();
    }

    /** Stops the operation with 403 CONSENT_REQUIRED unless the customer has agreed to the purpose. */
    public void require(long userId, ConsentPurpose purpose) {
        if (!has(userId, purpose)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONSENT_REQUIRED",
                    "Your consent is needed first: " + ConsentPolicy.text(purpose).title() + ".");
        }
    }

    @Transactional
    public ConsentDtos.View grant(long userId, ConsentPurpose purpose) {
        return record(userId, purpose, true, "USER");
    }

    @Transactional
    public ConsentDtos.View withdraw(long userId, ConsentPurpose purpose) {
        var view = record(userId, purpose, false, "USER");
        events.publishEvent(new ConsentWithdrawnEvent(userId, purpose));
        return view;
    }

    /** Demo accounts created by the fixture loader start with every consent granted; the row says so. */
    @Transactional
    public void grantAllForDemoFixture(long userId) {
        for (var text : ConsentPolicy.texts()) {
            if (!has(userId, text.purpose())) record(userId, text.purpose(), true, "DEMO_FIXTURE");
        }
    }

    /** Grants all consents as the customer (used by tests that stand in for a user who accepted every box). */
    @Transactional
    public void grantAll(long userId) {
        for (var text : ConsentPolicy.texts()) {
            if (!has(userId, text.purpose())) record(userId, text.purpose(), true, "USER");
        }
    }

    /** The consents in force right now, stored with a decision so it can be explained later. */
    public Map<String, Object> snapshot(long userId) {
        var states = states(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policyVersion", ConsentPolicy.VERSION);
        for (var text : ConsentPolicy.texts()) {
            State s = states.get(text.purpose());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("granted", s != null && s.effective());
            entry.put("decidedAt", s == null ? null : s.decidedAt().toString());
            result.put(text.purpose().name(), entry);
        }
        return result;
    }

    private ConsentDtos.View record(long userId, ConsentPurpose purpose, boolean granted, String source) {
        State current = states(userId).get(purpose);
        // Repeating the same decision under the same wording adds nothing to the audit trail.
        if (current == null || current.granted() != granted || !ConsentPolicy.VERSION.equals(current.policyVersion())) {
            var text = ConsentPolicy.text(purpose);
            jdbc.update("""
                    INSERT INTO consent_records (user_id, purpose, granted, policy_version, text_sha256, source)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, userId, purpose.name(), granted, ConsentPolicy.VERSION, text.sha256(), source);
        }
        return view(userId);
    }
}
