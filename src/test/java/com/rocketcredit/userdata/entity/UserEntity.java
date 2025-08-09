package com.rocketcredit.userdata.entity;

import javax.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "annual_income")
    private Double annualIncome = 50000.0;  // Default mock

    @Column(name = "credit_bureau_score")
    private Integer creditBureauScore = 650;  // Default mock (300-850)

    @Column(name = "social_consent")
    private Boolean socialConsent = false;

    @Column(name = "social_handles", columnDefinition = "jsonb")
    private String socialHandles;  // e.g., "{\"linkedin\":\"https://linkedin.com/in/user\", \"x\":\"@handle\"}"

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

    public Double getAnnualIncome() { return annualIncome; }
    public void setAnnualIncome(Double annualIncome) { this.annualIncome = annualIncome; }

    public Integer getCreditBureauScore() { return creditBureauScore; }
    public void setCreditBureauScore(Integer creditBureauScore) { this.creditBureauScore = creditBureauScore; }

    public Boolean getSocialConsent() { return socialConsent; }
    public void setSocialConsent(Boolean socialConsent) { this.socialConsent = socialConsent; }

    public String getSocialHandles() { return socialHandles; }
    public void setSocialHandles(String socialHandles) { this.socialHandles = socialHandles; }
}
