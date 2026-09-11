package com.rocketcredit.payment.controller;

import com.rocketcredit.payment.api.PaymentsApi;
import com.rocketcredit.payment.model.PaymentTransfer;
import com.rocketcredit.payment.model.Payment;
import com.rocketcredit.payment.model.PaymentPatch;
import com.rocketcredit.payment.model.PaymentStatus;
import com.rocketcredit.payment.model.CreateTransferRequest;
import com.rocketcredit.payment.model.Payee;
import com.rocketcredit.payment.service.PaymentTransferService;

import java.net.URI;

import org.openapitools.jackson.nullable.JsonNullable;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Service
@RestController
@Validated
public class PaymentsApiImpl implements PaymentsApi {
  private final PaymentTransferService service;

  public PaymentsApiImpl(PaymentTransferService service) { this.service = service; }

  @Override
  public ResponseEntity<Payment> paymentsTransfersPost(
    @Valid CreateTransferRequest body,
    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
  ) {
    var saved = service.createTransfer(
        idempotencyKey,
        body.getPartnerPaymentId(),
        body.getPartnerId(),
        body.getUserId(),
        body.getAmount(),
        body.getCurrency(),
        body.getPayee().getName(),
        body.getPayee().getEmail(),
        body.getPayee().getPartnerUserId(),
        body.getItems()
    );
    return ResponseEntity.created(URI.create("/payments/transfers/" + saved.getId()))
                         .body(map(saved));
  }

  @Override public ResponseEntity<Payment> getPaymentsTransfersId(Long id) {
    return ResponseEntity.ok(map(service.get(id)));
  }

  @Override public ResponseEntity<Payment> patchPaymentsTransfersId(Long id, @Valid PaymentPatch patch) {
    var e = service.patch(
        id,
        patch.getStatus() == null ? null : PaymentTransfer.Status.valueOf(patch.getStatus().name()),
        patch.getProvider(), patch.getProviderPaymentId(), patch.getTransferReference(), patch.getFailureReason()
    );
    return ResponseEntity.ok(map(e));
  }

  @Override public ResponseEntity<Payment> paymentsTransfersIdCancelPost(Long id) {
    return ResponseEntity.ok(map(service.cancel(id)));
  }

  private Payment map(PaymentTransfer e) {
    var p = new Payment();
    p.setId(e.getId());
    p.setStatus(PaymentStatus.fromValue(e.getStatus().name()));
    p.setPartnerPaymentId(e.getPartnerPaymentId());
    p.setPartnerId(e.getPartnerId());
    p.setUserId(e.getUserId().intValue());
    p.setAmount(e.getAmount());
    p.setCurrency(e.getCurrency());
    p.setPayee(new Payee(e.getPayeeName(), e.getPayeeId(), e.getPayeeEmail()));
    p.setProvider(JsonNullable.of(e.getProvider()));
    p.setProviderPaymentId(JsonNullable.of(e.getProviderPaymentId()));
    p.setTransferReference(JsonNullable.of(e.getTransferReference()));
    p.setFailureReason(e.getFailureReason());
    p.setItems(e.getItems());
    p.setCreatedAt(e.getCreatedAt());
    p.setUpdatedAt(e.getUpdatedAt());
    return p;
  }
}
