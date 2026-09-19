package com.rocketcredit.backend.transactions;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A seeded historical purchase at a demo partner. Not a credit application. */
@Entity
@Table(name = "transactions")
public class TransactionEntity {
    public enum Status { COMPLETED, REFUNDED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "fixture_id", nullable = false, unique = true, length = 80)
    private String fixtureId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "paid_on_time", nullable = false)
    private boolean paidOnTime = true;

    @Column(length = 200)
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TransactionEntity() {}

    public TransactionEntity(Long userId, Long partnerId, String fixtureId, BigDecimal amount,
                             LocalDate occurredOn, Status status, boolean paidOnTime, String description) {
        this.userId = userId;
        this.partnerId = partnerId;
        this.fixtureId = fixtureId;
        this.amount = amount;
        this.occurredOn = occurredOn;
        this.status = status;
        this.paidOnTime = paidOnTime;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getPartnerId() { return partnerId; }
    public String getFixtureId() { return fixtureId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public Status getStatus() { return status; }
    public boolean isPaidOnTime() { return paidOnTime; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
