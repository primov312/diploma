package com.rocketcredit.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {}

    public record Register(
            @NotBlank @Email @Size(max = 255) String email,
            // BCrypt only uses the first 72 bytes, so longer passwords are refused rather than silently truncated.
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 120) String displayName
    ) {
        @Override public String toString() { return "Register{email=<redacted>}"; }
    }

    public record Login(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 72) String password
    ) {
        @Override public String toString() { return "Login{email=<redacted>}"; }
    }
}
