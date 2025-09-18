package com.rocketcredit.repayment.model;

import java.math.BigDecimal;

public record InstallmentItem(
  int installmentNo, String dueDate, BigDecimal amount, String status, String providerChargeId
) {}