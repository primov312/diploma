package com.rocketcredit.user_data.webhook;

import com.rocketcredit.user_data.entity.*;
import com.rocketcredit.user_data.repo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/internal/repayment")
@RequiredArgsConstructor
public class RepaymentWebhookController {
  private static final Logger log = LoggerFactory.getLogger(RepaymentWebhookController.class);

  private final UserRepaymentPlanRepository planRepo;
  private final UserRepaymentInstallmentRepository instRepo;

  public record Installment(
      @Positive int number,
      @NotNull LocalDate dueDate,
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      @NotBlank String status
  ) {}

  public record PlanCreatedEvent(
      @NotBlank String planUid,
      @Positive long userId,
      @NotNull @DecimalMin("0.01") BigDecimal totalAmount,
      @NotBlank String currency,
      @Positive int installments,
      @NotNull List<@Valid Installment> schedule
  ) {}

  @PostMapping("/plan-created")
  @Transactional
  public ResponseEntity<Void> planCreated(@Valid @RequestBody PlanCreatedEvent evt) {
    // Idempotent upsert by planUid
    var maybe = planRepo.findByPlanUid(evt.planUid());
    if (maybe.isEmpty()) {
      var p = new UserRepaymentPlanEntity();
      p.setPlanUid(evt.planUid());
      p.setUserId(evt.userId());
      p.setTotalAmount(evt.totalAmount().setScale(2, java.math.RoundingMode.HALF_UP));
      p.setCurrency(evt.currency());
      p.setInstallments(evt.installments());
      p.setStatus("ACTIVE");
      planRepo.save(p);
    }

    // Insert installments (ignore duplicates)
    for (Installment it : evt.schedule()) {
      var existing = instRepo.findByPlanUidAndInstallmentNo(evt.planUid(), it.number());
      if (existing.isPresent()) continue;
      var e = new UserRepaymentInstallmentEntity();
      e.setPlanUid(evt.planUid());
      e.setInstallmentNo(it.number());
      e.setDueDate(it.dueDate());
      e.setAmount(it.amount().setScale(2, java.math.RoundingMode.HALF_UP));
      e.setCurrency(evt.currency());
      e.setStatus(it.status());
      instRepo.save(e);
    }
    log.info("repayment plan ingested uid={} userIdHash={}", evt.planUid(), Integer.toHexString(Long.toString(evt.userId()).hashCode()));
    return ResponseEntity.accepted().build();
  }
}

