package com.rocketcredit.repayment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="repayment_plans")
@Getter @Setter
public class PlanEntity {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  @Column(unique=true, nullable=false) String planUid;
  Long userId;
  String currency;
  BigDecimal totalAmount;
  Integer installments;
  String provider;
  String providerAccount;
  String providerCustomerId;
  String providerPaymentMethodId;
  String status = "ACTIVE";
  Instant createdAt = Instant.now();
}