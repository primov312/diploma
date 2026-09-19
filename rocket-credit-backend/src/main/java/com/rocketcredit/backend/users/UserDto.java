package com.rocketcredit.backend.users;

import java.time.OffsetDateTime;

/** Public view of a user. Deliberately has no password field of any kind. */
public record UserDto(Long id, String email, String displayName, OffsetDateTime createdAt) {
    public static UserDto from(UserEntity u) {
        return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getCreatedAt());
    }
}
