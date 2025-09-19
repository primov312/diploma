package com.rocketcredit.payment.model;

import java.math.BigDecimal;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * CreateTransferRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-23T23:31:22.594277+06:00[Asia/Bishkek]")
public class CreateTransferRequest {
  @NotBlank
  private String partnerPaymentId;

  private String partnerId;

  @NotNull @Positive
  private Long userId;

  @NotNull @DecimalMin(value = "0.01") @Digits(integer = 16, fraction = 2)
  private BigDecimal amount;

  @NotBlank @Pattern(regexp = "^[A-Z]{3}$")
  private String currency;

  @NotNull @Valid
  private Payee payee;

  @Valid
  private List<ItemSummary> items;

  /**
   * Default constructor
   * @deprecated Use {@link CreateTransferRequest#CreateTransferRequest(String, Integer, Double, String, Payee)}
   */
  @Deprecated
  public CreateTransferRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CreateTransferRequest(String partnerPaymentId, Long userId, BigDecimal amount, String currency, Payee payee) {
    this.partnerPaymentId = partnerPaymentId;
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.payee = payee;
  }

  public CreateTransferRequest partnerPaymentId(String partnerPaymentId) {
    this.partnerPaymentId = partnerPaymentId;
    return this;
  }

  /**
   * Checkout reference from gateway
   * @return partnerPaymentId
  */
  @NotNull 
  @Schema(name = "partnerPaymentId", description = "Checkout reference from gateway", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("partnerPaymentId")
  public String getPartnerPaymentId() {
    return partnerPaymentId;
  }

  public void setPartnerPaymentId(String partnerPaymentId) {
    this.partnerPaymentId = partnerPaymentId;
  }

  public CreateTransferRequest partnerId(String partnerId) {
    this.partnerId = partnerId;
    return this;
  }

  /**
   * Get partnerId
   * @return partnerId
  */
  
  @Schema(name = "partnerId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("partnerId")
  public String getPartnerId() {
    return partnerId;
  }

  public void setPartnerId(String partnerId) {
    this.partnerId = partnerId;
  }

  public CreateTransferRequest userId(Long userId) {
    this.userId = userId;
    return this;
  }

  /**
   * Get userId
   * @return userId
  */
  @NotNull 
  @Schema(name = "userId", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("userId")
  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public CreateTransferRequest amount(BigDecimal amount) {
    this.amount = amount;
    return this;
  }

  /**
   * Get amount
   * minimum: 0.01
   * @return amount
  */
  @NotNull @DecimalMin("0.01") 
  @Schema(name = "amount", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("amount")
  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public CreateTransferRequest currency(String currency) {
    this.currency = currency;
    return this;
  }

  /**
   * 3-letter ISO code
   * @return currency
  */
  @NotNull 
  @Schema(name = "currency", description = "3-letter ISO code", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("currency")
  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public CreateTransferRequest payee(Payee payee) {
    this.payee = payee;
    return this;
  }

  /**
   * Get payee
   * @return payee
  */
  @NotNull @Valid 
  @Schema(name = "payee", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("payee")
  public Payee getPayee() {
    return payee;
  }

  public void setPayee(Payee payee) {
    this.payee = payee;
  }

  public CreateTransferRequest item(ItemSummary item) {
    this.items.add(item);
    return this;
  }

  /**
   * Get items
   * @return items
  */
  @Valid 
  @Schema(name = "items", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("items")
  public List<ItemSummary> getItems() {
    return items;
  }

  public void setItems(List<ItemSummary> items) {
    this.items = items;
  }
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CreateTransferRequest createTransferRequest = (CreateTransferRequest) o;
    return Objects.equals(this.partnerPaymentId, createTransferRequest.partnerPaymentId) &&
        Objects.equals(this.partnerId, createTransferRequest.partnerId) &&
        Objects.equals(this.userId, createTransferRequest.userId) &&
        Objects.equals(this.amount, createTransferRequest.amount) &&
        Objects.equals(this.currency, createTransferRequest.currency) &&
        Objects.equals(this.payee, createTransferRequest.payee) &&
        Objects.equals(this.items, createTransferRequest.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(partnerPaymentId, partnerId, userId, amount, currency, payee, items);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreateTransferRequest {\n");
    sb.append("    partnerPaymentId: ").append(toIndentedString(partnerPaymentId)).append("\n");
    sb.append("    partnerId: ").append(toIndentedString(partnerId)).append("\n");
    sb.append("    userId: ").append(toIndentedString(userId)).append("\n");
    sb.append("    amount: ").append(toIndentedString(amount)).append("\n");
    sb.append("    currency: ").append(toIndentedString(currency)).append("\n");
    sb.append("    payee: ").append(toIndentedString(payee)).append("\n");
    sb.append("    items: ").append(toIndentedString(items)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}
