package com.rocketcredit.payment.service;

import com.rocketcredit.payment.model.PaymentTransfer;
import com.rocketcredit.payment.model.PaymentTransfer.Status;
import com.rocketcredit.payment.repository.PaymentTransferRepository;
import com.rocketcredit.payment.model.CreateTransferRequest; // <-- if you have your own DTOs, adjust names
import com.rocketcredit.payment.model.ItemSummary;

import jakarta.persistence.EntityNotFoundException;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;


@Service
public class PaymentTransferService {

    private final PaymentTransferRepository repo;

    public PaymentTransferService(PaymentTransferRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public PaymentTransfer createTransfer(
            String idempotencyKey,
            String partnerPaymentId,
            String partnerId,
            Long userId,
            BigDecimal amount,
            String currency,
            String payeeName,
            String payeeEmail,
            String payeeId,
            List<ItemSummary> items
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existingByIdem = repo.findByIdempotencyKey(idempotencyKey);
            if (existingByIdem.isPresent()) return existingByIdem.get();
        }
        var existingByPartner = repo.findByPartnerPaymentId(partnerPaymentId);
        if (existingByPartner.isPresent()) return existingByPartner.get();

        var e = new PaymentTransfer();
        e.setStatus(Status.INITIATED);
        e.setPartnerPaymentId(partnerPaymentId);
        e.setPartnerId(partnerId);
        e.setUserId(userId);
        e.setAmount(amount);
        e.setCurrency(currency);
        e.setPayeeName(payeeName);
        e.setPayeeEmail(payeeEmail);
        e.setPayeeId(payeeId);
        e.setItems(items);
        e.setProvider("NONE");
        e.setIdempotencyKey(idempotencyKey);

        // transferReference would come from provider/bank response
        e.setTransferReference("local_ref_" + System.currentTimeMillis());
        e.setStatus(Status.TRANSFERRED);

        return repo.save(e);
    }

    @Transactional(readOnly = true)
    public PaymentTransfer get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Transfer not found"));
    }

    @Transactional
    public PaymentTransfer patch(Long id, Status status, String provider, String providerPaymentId, String transferRef, JsonNullable<String> failureReason) {
        var e = get(id);
        if (status != null) e.setStatus(status);
        if (provider != null) e.setProvider(provider);
        if (providerPaymentId != null) e.setProviderPaymentId(providerPaymentId);
        if (transferRef != null) e.setTransferReference(transferRef);
        if (failureReason != null) e.setFailureReason(failureReason);
        return repo.save(e);
    }

    @Transactional
    public PaymentTransfer cancel(Long id) {
        var e = get(id);
        if (e.getStatus() == Status.TRANSFERRED) {
            throw new IllegalStateException("Cannot cancel: already TRANSFERRED");
        }
        e.setStatus(Status.CANCELED);
        return repo.save(e);
    }
}