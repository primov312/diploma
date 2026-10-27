// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.signals;

import com.rocketcredit.backend.consents.ConsentPurpose;
import com.rocketcredit.backend.consents.ConsentWithdrawnEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Withdrawing the digital footprint consent deletes what was collected under it: the stored location and social
 * reports and the Facebook connection (the saved login is revoked at Meta, best effort). The permission generation
 * moves on, so a run that is still in flight is discarded when it tries to save. Budgets that used location
 * reports are recalculated the next time the account is read.
 */
@Component
class FootprintErasure {
    private final JdbcTemplate jdbc;
    private final SocialConnectionService connections;

    FootprintErasure(JdbcTemplate jdbc, SocialConnectionService connections) {
        this.jdbc = jdbc;
        this.connections = connections;
    }

    @EventListener
    void onWithdrawn(ConsentWithdrawnEvent event) {
        if (event.purpose() != ConsentPurpose.DIGITAL_FOOTPRINT) return;
        long userId = event.userId();
        connections.disconnect(userId);
        jdbc.update("DELETE FROM demo_signal_reports WHERE user_id=?", userId);
        jdbc.update("UPDATE demo_signal_settings SET permission_generation = permission_generation + 1, updated_at = now() WHERE user_id=?", userId);
        jdbc.update("UPDATE financial_input_state SET generation = generation + 1 WHERE user_id=?", userId);
    }
}
