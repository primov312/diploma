package com.rocketcredit.repayment.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Primary
public class FakeChargeProvider implements ChargeProvider {
  @Override
  public ProvisionResult provision(ProvisionRequest req) {
    // Simulate creating a customer and attaching a payment method
    return new ProvisionResult(
      "cust_" + UUID.randomUUID(),
      "pm_" + UUID.randomUUID()
    );
  }

  @Override
  public ChargeResult charge(ChargeRequest req) {
    // Always succeed for dev/local usage
    return new ChargeResult(
      "SUCCEEDED",
      "ch_" + UUID.randomUUID(),
      null,
      null
    );
  }
}

