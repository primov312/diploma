// AI-assisted (Claude), 2026: staff accounts and the role-aware user view added; reviewed by the author.
package com.rocketcredit.backend.users;

import com.rocketcredit.backend.common.ApiException;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    /** Trim + lower-case; the same normalization is used for login and enforced by a DB CHECK. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserEntity register(String email, String rawPassword, String displayName) {
        return create(email, rawPassword, displayName, Role.CUSTOMER);
    }

    /**
     * Creates an account with the given role. Only {@link #register} (public, always CUSTOMER), the demo seed
     * and the administrator's "create seller" action call this; a seller's partner link is added by the caller
     * in the same transaction (the database checks it at commit).
     */
    @Transactional
    public UserEntity create(String email, String rawPassword, String displayName, Role role) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        var entity = new UserEntity(normalized, passwordEncoder.encode(rawPassword), displayName.trim(), role);
        try {
            return users.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // Two registrations raced; the unique constraint is the final arbiter.
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
    }

    @Transactional(readOnly = true)
    public UserEntity requireById(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    /** The signed-in user's view, with the partner for sellers. */
    @Transactional(readOnly = true)
    public UserDto view(Long id) {
        var user = requireById(id);
        if (user.getRole() != Role.SELLER) return UserDto.from(user);
        var partner = jdbc.queryForMap("""
                SELECT p.slug, p.display_name FROM seller_accounts s JOIN partners p ON p.id = s.partner_id
                WHERE s.user_id = ?""", id);
        return UserDto.from(user, (String) partner.get("slug"), (String) partner.get("display_name"));
    }
}
