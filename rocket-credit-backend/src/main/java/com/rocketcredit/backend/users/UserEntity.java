// AI-assisted (Claude), 2026: role and enabled flag added; reviewed by the author.
package com.rocketcredit.backend.users;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalized: lower-case and trimmed before save (enforced by a DB CHECK too). */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt hash. Never exposed through the API. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.CUSTOMER;

    /** A disabled account cannot sign in, and its open sessions end on their next request. */
    @Column(nullable = false)
    private boolean enabled = true;

    protected UserEntity() {}

    public UserEntity(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    /** Staff accounts (seller, administrator); public registration always uses the customer constructor. */
    public UserEntity(String email, String passwordHash, String displayName, Role role) {
        this(email, passwordHash, displayName);
        this.role = role;
    }

    /** Fixture constructor: seeded demo customers get a back-dated registration time. */
    public UserEntity(String email, String passwordHash, String displayName, OffsetDateTime createdAt) {
        this(email, passwordHash, displayName);
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
}
