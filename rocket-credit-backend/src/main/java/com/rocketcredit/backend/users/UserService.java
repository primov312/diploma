package com.rocketcredit.backend.users;

import com.rocketcredit.backend.common.ApiException;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /** Trim + lower-case; the same normalization is used for login and enforced by a DB CHECK. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserEntity register(String email, String rawPassword, String displayName) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        var entity = new UserEntity(normalized, passwordEncoder.encode(rawPassword), displayName.trim());
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
}
