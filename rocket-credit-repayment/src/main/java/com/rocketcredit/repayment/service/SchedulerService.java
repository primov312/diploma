package com.rocketcredit.repayment.service;

import com.rocketcredit.repayment.entity.InstallmentEntity;
import com.rocketcredit.repayment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Arrays;
import java.util.List;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class SchedulerService {
  private final InstallmentRepository instRepo;
  private final PlanRepository planRepo;
  private final ChargeProvider chargeProvider;

  @Value("${repayment.business.retryMax:3}") int retryMax;

  @Scheduled(cron = "${repayment.schedule.cron}", zone = "UTC")
  @Transactional
  public void runDaily() {
    var due = instRepo.findDueForProcessing(LocalDate.now(ZoneOffset.UTC));
    for (InstallmentEntity inst : due) attemptCharge(inst);
  }

  @Transactional
  public void attemptCharge(InstallmentEntity inst) {
    var plan = planRepo.findByPlanUid(inst.getPlanUid()).orElseThrow();

    inst.setStatus("PROCESSING");
    inst.setAttempts(inst.getAttempts()+1);
    inst.setLastAttemptAt(Instant.now());

    var idemp = plan.getPlanUid()+"-"+inst.getInstallmentNo()+"-"+inst.getAttempts();
    var desc  = "BNPL installment %d/%d (%s)".formatted(inst.getInstallmentNo(), plan.getInstallments(), plan.getPlanUid());

    var res = chargeProvider.charge(new ChargeProvider.ChargeRequest(
      plan.getProviderAccount(), plan.getProviderCustomerId(), plan.getProviderPaymentMethodId(),
      inst.getAmount(), plan.getCurrency(), idemp, desc
    ));

    switch (res.status()) {
      case "SUCCEEDED" -> {
        inst.setStatus("PAID"); inst.setProviderChargeId(res.providerChargeId()); inst.setLastError(null);
      }
      default -> {
        if (inst.getAttempts() < retryMax) { inst.setStatus("PENDING"); }
        else { inst.setStatus("FAILED"); }
        inst.setProviderChargeId(res.providerChargeId());
        inst.setLastError(res.errorCode()+":"+res.errorMessage());
      }
    }
    // inst is saved by @Transactional on method exit
  }
}