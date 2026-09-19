package com.rocketcredit.backend.users;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Synthetic income/expense figures. Values are demo fixtures, never entered by the browser. */
@Entity
@Table(name = "demo_financial_profiles")
public class DemoFinancialProfileEntity {
    public enum Source { FIXTURE, STARTER }

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "monthly_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "monthly_expenses", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyExpenses;

    @Column(name = "monthly_obligations", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyObligations;

    @Column(name = "profile_complete", nullable = false)
    private boolean profileComplete;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "synthetic_source", nullable = false, length = 20)
    private Source syntheticSource;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected DemoFinancialProfileEntity() {}

    public DemoFinancialProfileEntity(Long userId, BigDecimal monthlyIncome, BigDecimal monthlyExpenses,
                                      BigDecimal monthlyObligations, boolean profileComplete,
                                      boolean emailVerified, Source syntheticSource) {
        this.userId = userId;
        this.monthlyIncome = monthlyIncome;
        this.monthlyExpenses = monthlyExpenses;
        this.monthlyObligations = monthlyObligations;
        this.profileComplete = profileComplete;
        this.emailVerified = emailVerified;
        this.syntheticSource = syntheticSource;
    }

    public Long getUserId() { return userId; }
    public BigDecimal getMonthlyIncome() { return monthlyIncome; }
    public BigDecimal getMonthlyExpenses() { return monthlyExpenses; }
    public BigDecimal getMonthlyObligations() { return monthlyObligations; }
    public boolean isProfileComplete() { return profileComplete; }
    public boolean isEmailVerified() { return emailVerified; }
    public Source getSyntheticSource() { return syntheticSource; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
