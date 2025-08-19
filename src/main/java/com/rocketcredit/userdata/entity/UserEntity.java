package com.rocketcredit.userdata.entity;

import javax.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "users")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "kyc_passed")
    private Boolean kycPassed = true;

    @Column(name = "credit_limit")
    private Double creditLimit;

    @Column(name = "annual_income")
    private Double annualIncome = 50000.0;  // Default mock

    @Column(name = "credit_bureau_score")
    private Integer creditBureauScore = 650;  // Default mock (300-850)

    @Column(name = "social_consent")
    private Boolean socialConsent = false;

    @Type(type = "jsonb")
    @Column(name = "social_handles", columnDefinition = "jsonb")
    private Map<String, Object> socialHandles;  // e.g., "{\"linkedin\":\"https://linkedin.com/in/user\", \"x\":\"@handle\"}"

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private ZonedDateTime emailVerifiedAt;

    protected UserEntity() {}

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

    public Boolean getKycPassed() { return kycPassed; }
    public void setKycPassed(Boolean kycPassed) { this.kycPassed = kycPassed; }

    public Double getCreditLimit() { return creditLimit; }
    public void setCreditLimit(Double creditLimit) { this.creditLimit = creditLimit; }

    public Double getAnnualIncome() { return annualIncome; }
    public void setAnnualIncome(Double annualIncome) { this.annualIncome = annualIncome; }

    public Integer getCreditBureauScore() { return creditBureauScore; }
    public void setCreditBureauScore(Integer creditBureauScore) { this.creditBureauScore = creditBureauScore; }

    public Boolean getSocialConsent() { return socialConsent; }
    public void setSocialConsent(Boolean socialConsent) { this.socialConsent = socialConsent; }

    public Map<String, Object> getSocialHandles() { return socialHandles; }
    public void setSocialHandles(Map<String, Object> socialHandles) { this.socialHandles = socialHandles; }

    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }

    public ZonedDateTime getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(ZonedDateTime emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
}
