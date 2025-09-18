package com.rocketcredit.repayment.api;

import com.rocketcredit.repayment.model.CreatePlanRequest;
import com.rocketcredit.repayment.model.PlanResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

public interface RepaymentsApi {

  @PostMapping("/repayments")
  ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest req);

  @GetMapping("/repayments/{planUid}")
  ResponseEntity<PlanResponse> getPlan(@PathVariable String planUid);
}