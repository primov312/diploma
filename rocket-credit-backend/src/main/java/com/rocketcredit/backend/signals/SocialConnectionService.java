package com.rocketcredit.backend.signals;

import com.rocketcredit.backend.common.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Stores the owner's Facebook connection. Login through Facebook is what proves the account belongs to the user. */
@Service
public class SocialConnectionService {
    private final JdbcTemplate jdbc;
    private final FacebookClient facebook;
    private final TokenCipher cipher;
    private final SocialProperties props;

    public SocialConnectionService(JdbcTemplate jdbc, FacebookClient facebook, TokenCipher cipher, SocialProperties props) {
        this.jdbc = jdbc;
        this.facebook = facebook;
        this.cipher = cipher;
        this.props = props;
    }

    public SocialConnectionDtos.Status status(long userId) {
        return jdbc.query("SELECT display_name, connected_at, token_expires_at FROM social_connections WHERE user_id=?",
                rs -> rs.next()
                        ? new SocialConnectionDtos.Status(props.configured(), true, "FACEBOOK", rs.getString(1),
                                rs.getTimestamp(2).toInstant(), instant(rs.getTimestamp(3)))
                        : new SocialConnectionDtos.Status(props.configured(), false, null, null, null, null),
                userId);
    }

    public String authorizeUrl(String state) {
        requireConfigured();
        return facebook.authorizeUrl(state);
    }

    public void connect(long userId, String code) {
        requireConfigured();
        var token = facebook.exchangeCode(code);
        var profile = facebook.me(token.accessToken());
        try {
            jdbc.update("""
                    INSERT INTO social_connections (user_id, provider, provider_user_id, display_name, token_ciphertext, token_expires_at)
                    VALUES (?, 'FACEBOOK', ?, ?, ?, ?)
                    ON CONFLICT (user_id) DO UPDATE SET provider_user_id=EXCLUDED.provider_user_id,
                      display_name=EXCLUDED.display_name, token_ciphertext=EXCLUDED.token_ciphertext,
                      token_expires_at=EXCLUDED.token_expires_at, connected_at=now()
                    """, userId, profile.id(), profile.name(), cipher.encrypt(token.accessToken()),
                    token.expiresAt() == null ? null : Timestamp.from(token.expiresAt()));
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("SOCIAL_ACCOUNT_ALREADY_LINKED", "This Facebook account is already linked to another user.");
        }
    }

    public Optional<String> token(long userId) {
        return jdbc.query("SELECT token_ciphertext FROM social_connections WHERE user_id=?",
                rs -> rs.next() ? Optional.of(cipher.decrypt(rs.getString(1))) : Optional.<String>empty(), userId);
    }

    public void disconnect(long userId) {
        token(userId).ifPresent(facebook::revoke);
        jdbc.update("DELETE FROM social_connections WHERE user_id=?", userId);
    }

    private void requireConfigured() {
        if (!props.configured()) {
            throw ApiException.badRequest("SOCIAL_NOT_CONFIGURED", "Facebook login is not configured on this server (set META_APP_ID and META_APP_SECRET).");
        }
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
