package com.rocketcredit.gateway.service;

import com.rocketcredit.gateway.api.CheckoutRequest;
import com.rocketcredit.gateway.api.CheckoutResponse;
import com.rocketcredit.gateway.clients.*;
import com.rocketcredit.gateway.clients.UserDataClient.ResolveUserRequest;
import com.rocketcredit.gateway.clients.UserDataClient.TransactionDto;
import com.rocketcredit.gateway.clients.UserDataClient.User;
import com.rocketcredit.gateway.clients.NotificationClient.NotifyRequest;
import com.rocketcredit.gateway.clients.PaymentClient.ItemDto;
import com.rocketcredit.gateway.clients.RepaymentClient.PlanResponse;
import com.rocketcredit.gateway.model.Installment;
import com.rocketcredit.gateway.model.Item;
import com.rocketcredit.gateway.model.Transaction;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CheckoutServiceImpl implements CheckoutService {

    private final UserDataClient uds;
    private final CreditAnalysisClient cas;
    private final PaymentClient pay;
    private final RepaymentClient rep;
    private final NotificationClient notif;

    private static final int MAX_TRANSACTIONS = 600;
    private static final int MAX_ITEMS = 100;

    public CheckoutServiceImpl(UserDataClient uds, CreditAnalysisClient cas, PaymentClient pay, RepaymentClient rep, NotificationClient notif) {
        this.uds = uds; this.cas = cas; this.pay = pay; this.rep = rep; this.notif = notif;
    }

    @Override
    public CheckoutResponse runCheckout(CheckoutRequest req) {
        // 0) Validate partner request (minimal)
        if (req.getPartnerPaymentId() == null || req.getAmount() == null || req.getCurrency() == null
                || req.getInstallmentDurationMonths() == null || req.getBuyer() == null
                || req.getBuyer().getCardToken() == null) {
            throw new IllegalArgumentException("Missing required fields in checkout request");
        }

        var buyer = req.getBuyer();
        List<Transaction> transactions =
            Optional.ofNullable(buyer.getTransactions()).orElse(List.of());
        List<Item> items =
            Optional.ofNullable(buyer.getItems()).orElse(List.of());

        if (transactions.size() > MAX_TRANSACTIONS) {
            throw new IllegalArgumentException("Too many transactions; max " + MAX_TRANSACTIONS);
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("Too many items; max " + MAX_ITEMS);
        }

        // 1) Resolve user
        var r = new ResolveUserRequest(
            req.getBuyer().getPartnerUserId(),
            req.getBuyer().getEmail(),
            req.getBuyer().getName(),
            new UserDataClient.PaymentMethodDto(req.getBuyer().getCardToken()),
            transactions.stream()
                .map(tnx -> {
                    TransactionDto dto = new TransactionDto();
                    dto.setId(tnx.getId());
                    dto.setAmount(tnx.getAmount());
                    dto.setMethod(tnx.getMethod());
                    dto.setDate(tnx.getDate());
                    return dto;
                })
                .collect(Collectors.toList())
        );
        User user = uds.resolve(r);
        long userId = user.getId();

        // 2) Credit analysis (requires 'cartTotal')
        var claim = uds.createFeatureClaim(userId);
        var decision = cas.scoreWithClaim(userId, req.getAmount().doubleValue(), claim);

        boolean approved = Boolean.TRUE.equals(decision.getApproved());
        String reason = coarsenDecisionReason(decision.getFinal_reason());

        if (!approved) {
            CheckoutResponse out = new CheckoutResponse();
            out.setApproved(false);
            out.setUserId(userId);
            out.setPartnerPaymentId(req.getPartnerPaymentId());
            out.setReason(JsonNullable.of(reason));
            out.setSchedule(List.of());
            return out;
        }

        // 3) Payment
        var paymentResp = pay.create(new PaymentClient.CreatePaymentRequest(
            userId, req.getPartnerPaymentId(), req.getPartnerId(), req.getAmount(), req.getCurrency(), req.getBuyer(), items.stream().
                map(item -> {
                    ItemDto dto = new ItemDto();
                    dto.setSku(item.getSku());
                    dto.setName(item.getName());
                    dto.setPrice(item.getPrice());
                    dto.setQuantity(item.getQuantity());
                    return dto;
                })
                .collect(Collectors.toList())
            ));
        String paymentId = paymentResp.getPaymentId();
        if ("failed".equalsIgnoreCase(paymentResp.getStatus())) {
            CheckoutResponse out = new CheckoutResponse();
            out.setApproved(false);
            out.setUserId(userId);
            out.setPartnerPaymentId(req.getPartnerPaymentId());
            out.setReason(JsonNullable.of("PAYMENT_FAILED"));
            out.setSchedule(List.of());
            return out;
        }

        // 4) Repayment plan
        var plan = rep.createPlan(new RepaymentClient.PlanRequest(
            userId,
            paymentId,
            req.getPartnerPaymentId(),
            req.getInstallmentDurationMonths(),
            req.getAmount(),
            req.getCurrency(),
            req.getBuyer()
        ));

        // Validate repayment schedule integrity before returning it
        validateRepaymentPlan(plan, req.getAmount(), req.getInstallmentDurationMonths());

        // 5) Notify (best-effort)
        //notif.send(new NotifyRequest(userId, paymentId, plan.getRepaymentPlanId(), req.getBuyer().getEmail()));

        // 6) Build response
        CheckoutResponse out = new CheckoutResponse();
        out.setApproved(true);
        out.setUserId(userId);
        out.setPaymentId(paymentId);
        out.setPartnerPaymentId(req.getPartnerPaymentId());
        out.setRepaymentPlanId(plan.getRepaymentPlanId());
        out.setInstallmentDurationMonths(req.getInstallmentDurationMonths());

        out.setSchedule(
            plan.getSchedule().stream().map(i -> {
                var inst = new Installment();
                inst.setDueDate(java.time.LocalDate.parse(i.getDueDate()));
                inst.setAmount(i.getAmount());
                return inst;
            }).toList()
        );
        return out;
    }

    @Override
    public CheckoutResponse toErrorResponse(CheckoutRequest req, Throwable t) {
        String reason = coarsenErrorReason(t);

        CheckoutResponse err = new CheckoutResponse();
        err.setApproved(false);
        err.setPartnerPaymentId(req.getPartnerPaymentId());
        err.setInstallmentDurationMonths(req.getInstallmentDurationMonths());
        // Avoid leaking internals; set a coarse reason
        err.setReason(JsonNullable.of(reason));
        err.setSchedule(List.of());
        return err;
    }

    private String coarsenDecisionReason(String raw) {
        // Do not leak CAS internals; return a single coarse code for denials
        return "CREDIT_DENIED";
    }

    private String coarsenErrorReason(Throwable t) {
        if (t instanceof IllegalArgumentException) {
            return "INVALID_REQUEST";
        }
        if (t instanceof com.rocketcredit.gateway.clients.CircuitOpenException
                || t instanceof com.rocketcredit.gateway.clients.BulkheadFullException) {
            return "SERVICE_UNAVAILABLE";
        }
        if (t instanceof org.springframework.web.client.ResourceAccessException
                || t instanceof java.net.ConnectException
                || t instanceof java.net.SocketTimeoutException) {
            return "SERVICE_UNAVAILABLE";
        }
        if (t instanceof org.springframework.web.client.HttpStatusCodeException hsce) {
            try {
                return hsce.getStatusCode().is5xxServerError() ? "SERVICE_UNAVAILABLE" : "UPSTREAM_ERROR";
            } catch (Throwable ignore) {
                return "UPSTREAM_ERROR";
            }
        }
        if (t instanceof IllegalStateException) {
            return "UPSTREAM_ERROR";
        }
        return "INTERNAL_ERROR";
    }

    private void validateRepaymentPlan(PlanResponse plan,
                                       BigDecimal expectedTotal,
                                       int expectedInstallments) {
        if (plan == null || plan.getSchedule() == null) {
            throw new IllegalStateException("INVALID_SCHEDULE_NULL");
        }
        var schedule = plan.getSchedule();
        if (schedule.size() != expectedInstallments) {
            throw new IllegalStateException("INVALID_SCHEDULE_SIZE");
        }
        // Validate each installment and compute sum
        LocalDate prev = null;
        BigDecimal sum = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        for (com.rocketcredit.gateway.clients.RepaymentClient.Installment inst : schedule) {
            if (inst == null) throw new IllegalStateException("INVALID_SCHEDULE_ENTRY_NULL");
            if (inst.getAmount() == null || inst.getAmount().signum() <= 0) {
                throw new IllegalStateException("INVALID_SCHEDULE_AMOUNT");
            }
            sum = sum.add(inst.getAmount());
            String due = inst.getDueDate();
            if (due == null || due.isBlank()) throw new IllegalStateException("INVALID_SCHEDULE_DUE_DATE");
            LocalDate dd;
            try { dd = LocalDate.parse(due); } catch (DateTimeParseException e) {
                throw new IllegalStateException("INVALID_SCHEDULE_DUE_DATE_FORMAT");
            }
            if (dd.isBefore(today)) {
                throw new IllegalStateException("INVALID_SCHEDULE_PAST_DUE_DATE");
            }
            if (prev != null && !dd.isAfter(prev)) {
                throw new IllegalStateException("INVALID_SCHEDULE_ORDER");
            }
            prev = dd;
        }
        // Amount sum must match expected total within 1 cent tolerance
        BigDecimal diff = sum.subtract(expectedTotal).abs();
        if (diff.compareTo(new BigDecimal(0.01)) > 0) {
            throw new IllegalStateException("INVALID_SCHEDULE_TOTAL_MISMATCH");
        }
    }
}
