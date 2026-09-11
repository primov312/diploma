package com.rocketcredit.payment.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;


import org.openapitools.jackson.nullable.JsonNullable;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonType;


@Entity
@Table(name = "payments",
       indexes = {
           @Index(name = "ix_payment_status", columnList = "status"),
           @Index(name = "ix_payment_partner_id", columnList = "partner_id"),
           @Index(name = "ix_payment_user_id", columnList = "user_id")
       })
public class PaymentTransfer {

    public enum Status { INITIATED, TRANSFERRED, FAILED, CANCELED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(name = "partner_payment_id", nullable = false, length = 128, unique = true)
    private String partnerPaymentId;

    @Column(name = "partner_id", length = 128)
    private String partnerId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payee_name", columnDefinition = "text")
    private String payeeName;

    @Column(name = "payee_id", columnDefinition = "text")
    private String payeePartnerId;

    @Column(name = "payee_email", columnDefinition = "text")
    private String payeeEmail;

    @Column(length = 128)
    private String provider; // e.g. "NONE", "STRIPE"

    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;

    @Column(name = "transfer_reference", length = 128)
    private String transferReference;

    @Column(name = "failure_reason", columnDefinition = "text")
    private JsonNullable<String> failureReason;

    @Type(JsonType.class)
    @Column(name = "items", columnDefinition = "jsonb")
    private List<ItemSummary> items;

    @Column(name = "idempotency_key", length = 128, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = Status.INITIATED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }


    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public Status getStatus() {
        return status;
    }
    public void setStatus(Status status) {
        this.status = status;
    }

    public String getPartnerPaymentId() {
        return partnerPaymentId;
    }
    public void setPartnerPaymentId(String partnerPaymentId) {
        this.partnerPaymentId = partnerPaymentId;
    }

    public String getPartnerId() {
        return partnerId;
    }
    public void setPartnerId(String partnerId) {
        this.partnerId = partnerId;
    }

    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPayeeName() {
        return payeeName;
    }
    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    public String getPayeeId() {
        return payeePartnerId;
    }
    public void setPayeeId(String payeePartnerId) {
        this.payeePartnerId = payeePartnerId;
    }

    public String getPayeeEmail() {
        return payeeEmail;
    }
    public void setPayeeEmail(String payeeEmail) {
        this.payeeEmail = payeeEmail;
    }

    public String getProvider() {
        return provider;
    }
    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }
    public void setProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
    }

    public String getTransferReference() {
        return transferReference;
    }
    public void setTransferReference(String transferReference) {
        this.transferReference = transferReference;
    }

    public JsonNullable<String> getFailureReason() {
        return failureReason;
    }
    public void setFailureReason(JsonNullable<String> failureReason) {
        this.failureReason = failureReason;
    }

    public List<ItemSummary> getItems() {
        return items;
    }

    public void setItems(List<ItemSummary> items) {
        this.items = items;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}