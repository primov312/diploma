package com.rocketcredit.repayment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name="repayment_installments",
  uniqueConstraints=@UniqueConstraint(columnNames={"plan_uid","installment_no"}))
@Getter @Setter
public class InstallmentEntity {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  @Column(name="plan_uid", nullable=false) String planUid;
  @Column(name="installment_no", nullable=false) Integer installmentNo;
  LocalDate dueDate;
  BigDecimal amount;
  String currency;
  String status = "PENDING";
  Integer attempts = 0;
  String providerChargeId;
  Instant lastAttemptAt;
  String lastError;
}
