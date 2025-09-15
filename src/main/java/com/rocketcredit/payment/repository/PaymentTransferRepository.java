package com.rocketcredit.payment.repository;

import  com.rocketcredit.payment.model.PaymentTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTransferRepository extends JpaRepository<PaymentTransfer, Long> {
    Optional<PaymentTransfer> findByPartnerPaymentId(String partnerPaymentId);
    Optional<PaymentTransfer> findByIdempotencyKey(String idempotencyKey);
    Page<PaymentTransfer> findByStatus(PaymentTransfer.Status status, Pageable pageable);
    Page<PaymentTransfer> findByUserId(Long userId, Pageable pageable);
    Page<PaymentTransfer> findByPartnerId(String partnerId, Pageable pageable);
}