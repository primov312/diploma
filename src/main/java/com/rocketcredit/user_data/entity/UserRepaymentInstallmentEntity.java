package com.rocketcredit.user_data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "user_repayment_installments",
  uniqueConstraints = @UniqueConstraint(name = "uk_plan_installment", columnNames = {"plan_uid", "installment_no"}),
  indexes = { @Index(name = "ix_uri_plan_uid", columnList = "plan_uid") })
@Getter @Setter
public class UserRepaymentInstallmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_uid", nullable = false)
    private String planUid;

    @Column(name = "installment_no", nullable = false)
    private Integer installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";
}

