package com.rocketcredit.repayment.model;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

public record CreatePlanRequest(
  @Positive long userId,
  @NotBlank String paymentId,
  @NotBlank String partnerPaymentId,
  @Positive int installmentDurationMonths,
  @NotNull @Positive BigDecimal totalAmount,
  @NotBlank String currency,
  @Valid Buyer buyer
  ) {} 
