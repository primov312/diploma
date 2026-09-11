package com.rocketcredit.repayment.controller;

import com.rocketcredit.repayment.api.*;
import com.rocketcredit.repayment.model.*;
import com.rocketcredit.repayment.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RepaymentsApiImpl implements RepaymentsApi {

  private final RepaymentService service;
  private final ChargeProvider chargeProvider; // inject Stripe/Adyen impl later

  @Override
  public ResponseEntity<PlanResponse> createPlan(CreatePlanRequest req) {
    var resp = service.createPlan(req, chargeProvider);
    return ApiUtil.ok(resp);
  }

  @Override
  public ResponseEntity<PlanResponse> getPlan(String planUid) {
    return ApiUtil.ok(service.getPlan(planUid));
  }
}