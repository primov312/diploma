package com.rocketcredit.repayment.model;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.*;

public record Buyer(
  @Email String email,
  @NotBlank String name,
  @NotBlank String cardToken,
  List <Item> items
) {
    public record Item(String sku, String name, @Positive BigDecimal price, @Positive int quantity) {}
}