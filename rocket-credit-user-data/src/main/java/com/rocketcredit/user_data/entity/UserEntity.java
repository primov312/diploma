package com.rocketcredit.user_data.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonType;

import java.time.ZonedDateTime;
import java.util.Map;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "partner_user_id")
    private String partnerUserId;

    @Column(name = "cardToken")
    private String cardToken;

    @Column(name = "kyc_passed")
    private Boolean kyc_passed;

    @CreationTimestamp
    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "email_verified_at")
    private ZonedDateTime emailVerifiedAt;

    public UserEntity() {}

    public UserEntity(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPartnerUserId() { return partnerUserId; }
    public void setPartnerUserId(String partnerUserId) { this.partnerUserId = partnerUserId; }

    public String getCardToken() { return cardToken; }
    public void setCardToken(String cardToken) { this.cardToken = cardToken; }

    public Boolean isKysPassed() { return kyc_passed; }
    public void setKysPassed(Boolean kyc_passed) { this.kyc_passed = kyc_passed; }

    public ZonedDateTime getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(ZonedDateTime emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
}
