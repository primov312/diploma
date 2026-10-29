// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.admin;

import com.rocketcredit.backend.audit.AuditLog;
import com.rocketcredit.backend.auth.AuthenticatedUser;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.common.Paging;
import com.rocketcredit.backend.users.Role;
import com.rocketcredit.backend.users.UserService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account management. Roles are never editable: a seller is created as a seller, customers come only
 * from public sign-up. A disabled account cannot sign in, and AccountStateFilter ends its open sessions on
 * their next request. Every change writes one audit row in the same transaction.
 */
@Service
public class AdminAccountService {
    private static final String USER_SELECT = """
            SELECT u.id, u.email, u.display_name, u.role, u.enabled, u.created_at, p.slug, p.display_name AS partner_name
            FROM users u LEFT JOIN seller_accounts s ON s.user_id = u.id LEFT JOIN partners p ON p.id = s.partner_id""";

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final PasswordEncoder encoder;
    private final AuditLog audit;

    public AdminAccountService(JdbcTemplate jdbc, UserService users, PasswordEncoder encoder, AuditLog audit) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminDtos.Page<AdminDtos.User> list(String role, String q, int page, int size) {
        Paging.check(page, size);
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        List<Object> args = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            try { Role.valueOf(role); } catch (IllegalArgumentException e) { throw ApiException.badRequest("INVALID_ROLE", "Unknown role."); }
            where.append(" AND u.role = ?");
            args.add(role);
        }
        if (q != null && !q.isBlank()) {
            // strpos instead of LIKE, so % and _ in the search text are plain characters
            where.append(" AND (strpos(u.email, lower(?)) > 0 OR strpos(lower(u.display_name), lower(?)) > 0)");
            args.add(q.trim());
            args.add(q.trim());
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM users u" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var items = jdbc.query(USER_SELECT + where + " ORDER BY u.id DESC LIMIT ? OFFSET ?", (rs, n) -> row(rs), args.toArray());
        return new AdminDtos.Page<>(items, page, size, total);
    }

    private static AdminDtos.User row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AdminDtos.User(rs.getLong("id"), rs.getString("email"), rs.getString("display_name"), rs.getString("role"),
                rs.getBoolean("enabled"), rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getString("slug"), rs.getString("partner_name"));
    }

    private AdminDtos.User user(long id) {
        return jdbc.query(USER_SELECT + " WHERE u.id = ?", (rs, n) -> row(rs), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("User"));
    }

    /**
     * A retried submission hits the unique e-mail constraint and gets the same 409 EMAIL_TAKEN, so a
     * double click never creates two sellers.
     */
    @Transactional
    public AdminDtos.User createSeller(AuthenticatedUser admin, AdminDtos.CreateSeller body) {
        String slug = jdbc.query("SELECT slug FROM partners WHERE id = ?", (rs, n) -> rs.getString(1), body.partnerId())
                .stream().findFirst().orElseThrow(() -> ApiException.badRequest("UNKNOWN_PARTNER", "Unknown partner."));
        var created = users.create(body.email(), body.password(), body.displayName(), Role.SELLER);
        jdbc.update("INSERT INTO seller_accounts(user_id, partner_id) VALUES (?, ?)", created.getId(), body.partnerId());
        audit.record(admin, "ADMIN_SELLER_CREATED", "USER", created.getId(), Map.of("email", created.getEmail(), "partner", slug));
        return user(created.getId());
    }

    @Transactional
    public AdminDtos.User setEnabled(AuthenticatedUser admin, long id, boolean enabled) {
        // Lock the enabled administrators first, so two administrators disabling each other cannot both succeed.
        var enabledAdmins = jdbc.queryForList("SELECT id FROM users WHERE role = 'ADMIN' AND enabled ORDER BY id FOR UPDATE", Long.class);
        var target = user(id);
        if (!enabled) {
            if (id == admin.getId()) throw ApiException.conflict("OWN_ACCOUNT", "You cannot disable your own account.");
            if ("ADMIN".equals(target.role()) && target.enabled() && enabledAdmins.stream().noneMatch(other -> other != id))
                throw ApiException.conflict("LAST_ADMIN", "The last enabled administrator cannot be disabled.");
        }
        if (target.enabled() != enabled) {
            jdbc.update("UPDATE users SET enabled = ? WHERE id = ?", enabled, id);
            audit.record(admin, enabled ? "ADMIN_USER_ENABLED" : "ADMIN_USER_DISABLED", "USER", id, Map.of("role", target.role()));
        }
        return user(id);
    }

    /** The administrator types a temporary password and passes it on; nothing secret is returned or logged. */
    @Transactional
    public void resetPassword(AuthenticatedUser admin, long id, AdminDtos.ResetPassword body) {
        var target = user(id);
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", encoder.encode(body.password()), id);
        var details = new LinkedHashMap<String, Object>();
        details.put("role", target.role());
        audit.record(admin, "ADMIN_PASSWORD_RESET", "USER", id, details);
    }
}
