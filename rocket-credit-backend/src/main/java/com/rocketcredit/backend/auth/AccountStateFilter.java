// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes an administrator's change to an account take effect on that user's next request.
 *
 * <p>Sessions live in the Java process and keep the principal from the moment of login. Instead of
 * tracking every session of a user (a SessionRegistry), this filter re-reads the account on every
 * signed-in API request: one primary-key query joining the seller link. If the account is gone,
 * disabled, or its role or partner no longer match the session, the session is invalidated and the
 * request continues as anonymous, so a protected endpoint answers 401 exactly as for an expired session.
 * The cost is one indexed query per signed-in API call, which is negligible for this application.
 */
public class AccountStateFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AccountStateFilter.class);
    private final JdbcTemplate jdbc;

    public AccountStateFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user && !stillValid(user)) {
            log.info("session ended after an account change id={}", user.getId());
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private boolean stillValid(AuthenticatedUser user) {
        return jdbc.query("""
                        SELECT u.role, u.enabled, s.partner_id FROM users u
                        LEFT JOIN seller_accounts s ON s.user_id = u.id WHERE u.id = ?""",
                        (rs, n) -> rs.getBoolean("enabled")
                                && user.getRole().name().equals(rs.getString("role"))
                                && Objects.equals(user.getPartnerId(), rs.getObject("partner_id", Long.class)),
                        user.getId())
                .stream().findFirst().orElse(false);
    }
}
