package com.rocketcredit.payment.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.rocketcredit.payment.model.PaymentStatus;
import java.util.Arrays;
import org.openapitools.jackson.nullable.JsonNullable;
import java.util.NoSuchElementException;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * PaymentPatch
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-23T23:31:22.594277+06:00[Asia/Bishkek]")
public class PaymentPatch {

  private PaymentStatus status;

  private String provider;

  private String providerPaymentId;

  private String transferReference;

  private JsonNullable<String> failureReason = JsonNullable.undefined();

  public PaymentPatch status(PaymentStatus status) {
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

  public PaymentPatch provider(String provider) {
    this.provider = provider;
    return this;
  }

  /**
   * Get provider
   * @return provider
  */
  
  @Schema(name = "provider", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("provider")
  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public PaymentPatch providerPaymentId(String providerPaymentId) {
    this.providerPaymentId = providerPaymentId;
    return this;
  }

  /**
   * Get providerPaymentId
   * @return providerPaymentId
  */
  
  @Schema(name = "providerPaymentId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("providerPaymentId")
  public String getProviderPaymentId() {
    return providerPaymentId;
  }

  public void setProviderPaymentId(String providerPaymentId) {
    this.providerPaymentId = providerPaymentId;
  }

  public PaymentPatch transferReference(String transferReference) {
    this.transferReference = transferReference;
    return this;
  }

  /**
   * Get transferReference
   * @return transferReference
  */
  
  @Schema(name = "transferReference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("transferReference")
  public String getTransferReference() {
    return transferReference;
  }

  public void setTransferReference(String transferReference) {
    this.transferReference = transferReference;
  }

  public PaymentPatch failureReason(String failureReason) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PaymentPatch paymentPatch = (PaymentPatch) o;
    return Objects.equals(this.status, paymentPatch.status) &&
        this.provider.equals(paymentPatch.provider) &&
        this.providerPaymentId.equals(paymentPatch.providerPaymentId) &&
        this.transferReference.equals(paymentPatch.transferReference) &&
        equalsNullable(this.failureReason, paymentPatch.failureReason);
  }

  private static <T> boolean equalsNullable(JsonNullable<T> a, JsonNullable<T> b) {
    return a == b || (a != null && b != null && a.isPresent() && b.isPresent() && Objects.deepEquals(a.get(), b.get()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, hashCodeNullable(JsonNullable.of(provider)), hashCodeNullable(JsonNullable.of(providerPaymentId)), hashCodeNullable(JsonNullable.of(transferReference)), hashCodeNullable(failureReason));
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
    sb.append("class PaymentPatch {\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    provider: ").append(toIndentedString(provider)).append("\n");
    sb.append("    providerPaymentId: ").append(toIndentedString(providerPaymentId)).append("\n");
    sb.append("    transferReference: ").append(toIndentedString(transferReference)).append("\n");
    sb.append("    failureReason: ").append(toIndentedString(failureReason)).append("\n");
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

