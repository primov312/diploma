package com.rocketcredit.backend.auth;

import com.rocketcredit.backend.users.UserRepository;
import com.rocketcredit.backend.users.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DbUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public DbUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(UserService.normalizeEmail(email))
                .map(u -> new AuthenticatedUser(u.getId(), u.getEmail(), u.getPasswordHash()))
                // DaoAuthenticationProvider converts this into BadCredentialsException,
                // so unknown emails and wrong passwords are indistinguishable to the client.
                .orElseThrow(() -> new UsernameNotFoundException("no such user"));
    }
}
