package com.rocketcredit.repayment.service;

import java.math.BigDecimal;

public interface ChargeProvider {
  record ProvisionRequest(String providerAccount, String email, String name, String cardToken) {}
  record ProvisionResult(String customerId, String paymentMethodId) {}

  record ChargeRequest(String providerAccount, String customerId, String paymentMethodId,
                       BigDecimal amount, String currency, String idempotencyKey, String description) {}
  record ChargeResult(String status, String providerChargeId, String errorCode, String errorMessage) {}

  ProvisionResult provision(ProvisionRequest req);
  ChargeResult charge(ChargeRequest req);
}