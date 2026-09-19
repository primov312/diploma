package com.rocketcredit.backend.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The principal stored in the session. Carries the database ID so every
 * private query can be scoped to the logged-in user without a lookup.
 * The password hash is only present during authentication and erased after
 * the credentials have been checked (Spring Security erases them).
 */
public class AuthenticatedUser implements UserDetails {
    private final Long id;
    private final String email;
    private String passwordHash;

    public AuthenticatedUser(Long id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }

    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }

    @Override
    public String toString() {
        return "AuthenticatedUser{id=" + id + "}"; // never the email or hash in logs
    }
}
