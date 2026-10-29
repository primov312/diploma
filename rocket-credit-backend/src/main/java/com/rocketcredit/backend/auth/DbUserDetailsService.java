// AI-assisted (Claude), 2026: loads role, partner and enabled flag; reviewed by the author.
package com.rocketcredit.backend.auth;

import com.rocketcredit.backend.users.Role;
import com.rocketcredit.backend.users.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DbUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public DbUserDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return jdbc.query("""
                        SELECT u.id, u.email, u.password_hash, u.role, u.enabled, s.partner_id
                        FROM users u LEFT JOIN seller_accounts s ON s.user_id = u.id WHERE u.email = ?""",
                        (rs, n) -> new AuthenticatedUser(rs.getLong("id"), rs.getString("email"),
                                rs.getString("password_hash"), Role.valueOf(rs.getString("role")),
                                rs.getObject("partner_id", Long.class), rs.getBoolean("enabled")),
                        UserService.normalizeEmail(email))
                .stream().findFirst()
                // DaoAuthenticationProvider converts this into BadCredentialsException, and a disabled account
                // raises DisabledException; ApiExceptionHandler answers both with the same INVALID_CREDENTIALS,
                // so unknown emails, wrong passwords and disabled accounts are indistinguishable to the client.
                .orElseThrow(() -> new UsernameNotFoundException("no such user"));
    }
}
