package com.rocketcredit.payment.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.rocketcredit.payment.model.ItemSummary;
import com.rocketcredit.payment.model.Payee;
import com.rocketcredit.payment.model.PaymentStatus;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.NoSuchElementException;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Payment
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-23T23:31:22.594277+06:00[Asia/Bishkek]")
public class Payment {

  private Long id;

  private PaymentStatus status;

  private String partnerPaymentId;

  private String partnerId;

  private Integer userId;

  private BigDecimal amount;

  private String currency;

  private Payee payee;

  private JsonNullable<String> provider = JsonNullable.undefined();

  private JsonNullable<String> providerPaymentId = JsonNullable.undefined();

  private JsonNullable<String> transferReference = JsonNullable.undefined();

  private JsonNullable<String> failureReason = JsonNullable.undefined();

  private List<ItemSummary> items;

  @Valid
  private Map<String, Object> metadata = new HashMap<>();

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

  public Payment id(Long id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
  */
  
  @Schema(name = "id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Payment status(PaymentStatus status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
  */
  @Valid 
  @Schema(name = "status", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public Payment partnerPaymentId(String partnerPaymentId) {
    this.partnerPaymentId = partnerPaymentId;
    return this;
  }

  /**
   * Get partnerPaymentId
   * @return partnerPaymentId
  */
  
  @Schema(name = "partnerPaymentId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("partnerPaymentId")
  public String getPartnerPaymentId() {
    return partnerPaymentId;
  }

  public void setPartnerPaymentId(String partnerPaymentId) {
    this.partnerPaymentId = partnerPaymentId;
  }

  public Payment partnerId(String partnerId) {
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

  public Payment userId(Integer userId) {
    this.userId = userId;
    return this;
  }

  /**
   * Get userId
   * @return userId
  */
  
  @Schema(name = "userId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("userId")
  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public Payment amount(BigDecimal amount) {
    this.amount = amount;
    return this;
  }

  /**
   * Get amount
   * @return amount
  */
  
  @Schema(name = "amount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("amount")
  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public Payment currency(String currency) {
    this.currency = currency;
    return this;
  }

  /**
   * Get currency
   * @return currency
  */
  
  @Schema(name = "currency", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("currency")
  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Payment payee(Payee payee) {
    this.payee = payee;
    return this;
  }

  /**
   * Get payee
   * @return payee
  */
  @Valid 
  @Schema(name = "payee", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("payee")
  public Payee getPayee() {
    return payee;
  }

  public void setPayee(Payee payee) {
    this.payee = payee;
  }

  public Payment provider(String provider) {
    this.provider = JsonNullable.of(provider);
    return this;
  }

  /**
   * Get provider
   * @return provider
  */
  
  @Schema(name = "provider", example = "NONE", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("provider")
  public JsonNullable<String> getProvider() {
    return provider;
  }

  public void setProvider(JsonNullable<String> provider) {
    this.provider = provider;
  }

  public Payment providerPaymentId(String providerPaymentId) {
    this.providerPaymentId = JsonNullable.of(providerPaymentId);
    return this;
  }

  /**
   * Get providerPaymentId
   * @return providerPaymentId
  */
  
  @Schema(name = "providerPaymentId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("providerPaymentId")
  public JsonNullable<String> getProviderPaymentId() {
    return providerPaymentId;
  }

  public void setProviderPaymentId(JsonNullable<String> providerPaymentId) {
    this.providerPaymentId = providerPaymentId;
  }

  public Payment transferReference(String transferReference) {
    this.transferReference = JsonNullable.of(transferReference);
    return this;
  }

  /**
   * Get transferReference
   * @return transferReference
  */
  
  @Schema(name = "transferReference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("transferReference")
  public JsonNullable<String> getTransferReference() {
    return transferReference;
  }

  public void setTransferReference(JsonNullable<String> transferReference) {
    this.transferReference = transferReference;
  }

  public Payment failureReason(String failureReason) {
    this.failureReason = JsonNullable.of(failureReason);
    return this;
  }

  /**
   * Get failureReason
   * @return failureReason
  */
  
  @Schema(name = "failureReason", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("failureReason")
  public JsonNullable<String> getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(JsonNullable<String> failureReason) {
    this.failureReason = failureReason;
  }

  public Payment item(ItemSummary item) {
    items.add(item);
    return this;
  }

  /**
   * Get item
   * @return item
  */
  @Valid 
  @Schema(name = "item", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("item")
  public List<ItemSummary> getItems() {
    return items;
  }

  public void setItems(List<ItemSummary> items) {
    this.items = items;
  }

  public Payment metadata(Map<String, Object> metadata) {
    this.metadata = metadata;
    return this;
  }

  public Payment putMetadataItem(String key, Object metadataItem) {
    if (this.metadata == null) {
      this.metadata = new HashMap<>();
    }
    this.metadata.put(key, metadataItem);
    return this;
  }

  /**
   * Get metadata
   * @return metadata
  */
  
  @Schema(name = "metadata", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("metadata")
  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public Payment createdAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /**
   * Get createdAt
   * @return createdAt
  */
  @Valid 
  @Schema(name = "createdAt", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("createdAt")
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public Payment updatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  /**
   * Get updatedAt
   * @return updatedAt
  */
  @Valid 
  @Schema(name = "updatedAt", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("updatedAt")
  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Payment payment = (Payment) o;
    return Objects.equals(this.id, payment.id) &&
        Objects.equals(this.status, payment.status) &&
        Objects.equals(this.partnerPaymentId, payment.partnerPaymentId) &&
        Objects.equals(this.partnerId, payment.partnerId) &&
        Objects.equals(this.userId, payment.userId) &&
        Objects.equals(this.amount, payment.amount) &&
        Objects.equals(this.currency, payment.currency) &&
        Objects.equals(this.payee, payment.payee) &&
        equalsNullable(this.provider, payment.provider) &&
        equalsNullable(this.providerPaymentId, payment.providerPaymentId) &&
        equalsNullable(this.transferReference, payment.transferReference) &&
        equalsNullable(this.failureReason, payment.failureReason) &&
        Objects.equals(this.items, payment.items) &&
        Objects.equals(this.metadata, payment.metadata) &&
        Objects.equals(this.createdAt, payment.createdAt) &&
        Objects.equals(this.updatedAt, payment.updatedAt);
  }

  private static <T> boolean equalsNullable(JsonNullable<T> a, JsonNullable<T> b) {
    return a == b || (a != null && b != null && a.isPresent() && b.isPresent() && Objects.deepEquals(a.get(), b.get()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, status, partnerPaymentId, partnerId, userId, amount, currency, payee, hashCodeNullable(provider), hashCodeNullable(providerPaymentId), hashCodeNullable(transferReference), hashCodeNullable(failureReason), items, metadata, createdAt, updatedAt);
  }

  private static <T> int hashCodeNullable(JsonNullable<T> a) {
    if (a == null) {
      return 1;
    }
    return a.isPresent() ? Arrays.deepHashCode(new Object[]{a.get()}) : 31;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Payment {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    partnerPaymentId: ").append(toIndentedString(partnerPaymentId)).append("\n");
    sb.append("    partnerId: ").append(toIndentedString(partnerId)).append("\n");
    sb.append("    userId: ").append(toIndentedString(userId)).append("\n");
    sb.append("    amount: ").append(toIndentedString(amount)).append("\n");
    sb.append("    currency: ").append(toIndentedString(currency)).append("\n");
    sb.append("    payee: ").append(toIndentedString(payee)).append("\n");
    sb.append("    provider: ").append(toIndentedString(provider)).append("\n");
    sb.append("    providerPaymentId: ").append(toIndentedString(providerPaymentId)).append("\n");
    sb.append("    transferReference: ").append(toIndentedString(transferReference)).append("\n");
    sb.append("    failureReason: ").append(toIndentedString(failureReason)).append("\n");
    sb.append("    items: ").append(toIndentedString(items)).append("\n");
    sb.append("    metadata: ").append(toIndentedString(metadata)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
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

