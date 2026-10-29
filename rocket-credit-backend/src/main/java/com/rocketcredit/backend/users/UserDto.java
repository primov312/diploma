// AI-assisted (Claude), 2026: role and seller partner added; reviewed by the author.
package com.rocketcredit.backend.users;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * Public view of a user. Deliberately has no password field of any kind. Sellers also get the
 * partner they work for; for customers and administrators those fields are omitted.
 */
public record UserDto(Long id, String email, String displayName, OffsetDateTime createdAt, Role role,
                      @JsonInclude(JsonInclude.Include.NON_NULL) String partnerSlug,
                      @JsonInclude(JsonInclude.Include.NON_NULL) String partnerName) {
    public static UserDto from(UserEntity u) {
        return from(u, null, null);
    }

    public static UserDto from(UserEntity u, String partnerSlug, String partnerName) {
        return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getCreatedAt(), u.getRole(), partnerSlug, partnerName);
    }
}
