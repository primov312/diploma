package com.rocketcredit.backend.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionDto(Long id, String partnerSlug, String partnerName, BigDecimal amount, String currency,
                             LocalDate occurredOn, String status, boolean paidOnTime, String description) {}
