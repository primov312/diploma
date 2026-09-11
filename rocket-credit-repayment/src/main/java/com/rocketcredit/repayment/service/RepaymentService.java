package com.rocketcredit.repayment.service;

import com.rocketcredit.repayment.entity.*;
import com.rocketcredit.repayment.model.*;
import com.rocketcredit.repayment.repository.*;
import com.rocketcredit.repayment.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RepaymentService {
  private final PlanRepository planRepo;
  private final InstallmentRepository instRepo;
  private final IdGenerator idGen;
  private final UserDataNotifier notifier;

  @Value("${repayment.provider}")
  private String providerName;

  @Value("${repayment.providerAccount}")
  private String providerAccount;

  @Transactional
  public PlanResponse createPlan(CreatePlanRequest req, ChargeProvider provider) {
    // 1) Provision at PSP
    var prov = provider.provision(new ChargeProvider.ProvisionRequest(
      providerAccount, req.buyer().email(), req.buyer().name(), req.buyer().cardToken()
    ));

    // 2) Persist plan
    String planUid = idGen.generate("rpln");
    var plan = new PlanEntity();
    plan.setPlanUid(planUid);
    plan.setUserId(req.userId());
    plan.setCurrency(req.currency());
    plan.setTotalAmount(req.totalAmount());
    plan.setInstallments(req.installmentDurationMonths());
    plan.setProvider(providerName);
    plan.setProviderAccount(providerAccount);
    plan.setProviderCustomerId(prov.customerId());
    plan.setProviderPaymentMethodId(prov.paymentMethodId());
    planRepo.save(plan);

    // 3) Build schedule
    var amounts = splitEvenly(req.totalAmount(), req.installmentDurationMonths());
    LocalDate start = LocalDate.now(ZoneOffset.UTC);
    List<InstallmentItem> out = new ArrayList<>();

    for (int n=1; n<=req.installmentDurationMonths(); n++) {
      var inst = new InstallmentEntity();
      inst.setPlanUid(planUid);
      inst.setInstallmentNo(n);
      inst.setDueDate(start.plusMonths(n));
      inst.setAmount(amounts.get(n-1));
      inst.setCurrency(req.currency());
      instRepo.save(inst);
      out.add(new InstallmentItem(n, inst.getDueDate().toString(), inst.getAmount(), inst.getStatus(), null));
    }
    var response = new PlanResponse(planUid, out);

    // 4) Side-effect: notify User Data (best-effort, out-of-tx)
    try {
      notifier.notifyPlanCreated(planUid, req.userId(), req.totalAmount(), req.currency(), req.installmentDurationMonths(), out);
    } catch (Exception ignore) {}

    return response;
  }

  public PlanResponse getPlan(String planUid) {
    var plan = planRepo.findByPlanUid(planUid).orElseThrow();
    var list = instRepo.findByPlanUidOrderByInstallmentNo(planUid).stream()
      .map(i -> new InstallmentItem(i.getInstallmentNo(), i.getDueDate().toString(), i.getAmount(), i.getStatus(), i.getProviderChargeId()))
      .toList();
    return new PlanResponse(plan.getPlanUid(), list);
  }

  private List<BigDecimal> splitEvenly(BigDecimal total, int n) {
    var cents = total.movePointRight(2);
    var base = cents.divide(BigDecimal.valueOf(n), 0, java.math.RoundingMode.DOWN);
    var rem = cents.subtract(base.multiply(BigDecimal.valueOf(n))).intValue();
    List<BigDecimal> out = new ArrayList<>();
    for (int i=0;i<n;i++) {
      var c = base.add(BigDecimal.valueOf(i<rem?1:0));
      out.add(c.movePointLeft(2));
    }
    return out;
  }
}
