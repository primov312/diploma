package com.rocketcredit.repayment.model;

import java.util.List;

public record PlanResponse(
  String repaymentPlanId,
  List<InstallmentItem> schedule
) {}