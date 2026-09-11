package com.rocketcredit.user_data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_repayment_plans", indexes = {
        @Index(name = "ix_user_repayment_plans_user", columnList = "user_id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_user_repayment_plan_uid", columnNames = {"plan_uid"}))
@Getter @Setter
public class UserRepaymentPlanEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_uid", nullable = false)
    private String planUid;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "installments", nullable = false)
    private Integer installments;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

