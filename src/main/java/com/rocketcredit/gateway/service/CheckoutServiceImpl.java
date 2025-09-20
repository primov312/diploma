package com.rocketcredit.gateway.service;

import com.rocketcredit.gateway.api.CheckoutRequest;
import com.rocketcredit.gateway.api.CheckoutResponse;
import com.rocketcredit.gateway.clients.*;
import com.rocketcredit.gateway.clients.UserDataClient.ResolveUserRequest;
import com.rocketcredit.gateway.clients.UserDataClient.TransactionDto;
import com.rocketcredit.gateway.clients.UserDataClient.User;
import com.rocketcredit.gateway.clients.NotificationClient.NotifyRequest;
import com.rocketcredit.gateway.clients.PaymentClient.ItemDto;
import com.rocketcredit.gateway.model.Installment;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CheckoutServiceImpl implements CheckoutService {

    private final UserDataClient uds;
    private final CreditAnalysisClient cas;
    private final PaymentClient pay;
    private final RepaymentClient rep;
    private final NotificationClient notif;

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

        // 1) Resolve user
        var r = new ResolveUserRequest(
            req.getBuyer().getPartnerUserId(),
            req.getBuyer().getEmail(),
            req.getBuyer().getName(),
            req.getBuyer().getCardToken(),
            req.getBuyer().getTransactions().stream()
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
        String reason = Optional.ofNullable(decision.getFinal_reason()).orElse("UNSPECIFIED");

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
            userId, req.getPartnerPaymentId(), req.getAmount(), req.getCurrency(), req.getBuyer(), req.getBuyer().getItems().stream().
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
        String reason;
        if (t instanceof IllegalArgumentException) {
            reason = "INVALID_REQUEST";
        } else if (t instanceof org.springframework.web.client.ResourceAccessException
                || t instanceof java.net.ConnectException
                || t instanceof java.net.SocketTimeoutException) {
            reason = "UPSTREAM_UNAVAILABLE";
        } else if (t instanceof org.springframework.web.client.HttpStatusCodeException hsce) {
            reason = "DOWNSTREAM_" + hsce.getStatusCode().value();
        } else {
            reason = "INTERNAL_ERROR";
        }

        CheckoutResponse err = new CheckoutResponse();
        err.setApproved(false);
        err.setPartnerPaymentId(req.getPartnerPaymentId());
        err.setInstallmentDurationMonths(req.getInstallmentDurationMonths());
        // Avoid leaking internals; set a coarse reason
        err.setReason(org.openapitools.jackson.nullable.JsonNullable.of(reason));
        err.setSchedule(java.util.List.of());
        return err;
    }
}
