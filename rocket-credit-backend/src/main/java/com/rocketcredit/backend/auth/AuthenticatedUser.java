// AI-assisted (Claude), 2026: role, partner and enabled flag added; reviewed by the author.
package com.rocketcredit.backend.auth;

import com.rocketcredit.backend.users.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The principal stored in the session. Carries the database ID so every
 * private query can be scoped to the logged-in user without a lookup, plus the
 * role and, for sellers, the partner: seller endpoints take the partner from
 * here and never from the request. {@link AccountStateFilter} compares these
 * values with the database on every API request.
 * The password hash is only present during authentication and erased after
 * the credentials have been checked (Spring Security erases them).
 */
public class AuthenticatedUser implements UserDetails {
    private final Long id;
    private final String email;
    private String passwordHash;
    private final Role role;
    private final Long partnerId;
    private final boolean enabled;

    public AuthenticatedUser(Long id, String email, String passwordHash, Role role, Long partnerId, boolean enabled) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.partnerId = partnerId;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public Role getRole() { return role; }
    /** The seller's partner; null for customers and administrators. */
    public Long getPartnerId() { return partnerId; }

    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }

    @Override
    public String toString() {
        return "AuthenticatedUser{id=" + id + ", role=" + role + "}"; // never the email or hash in logs
    }
}
