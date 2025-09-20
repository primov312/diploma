package com.rocketcredit.gateway.api;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rocketcredit.gateway.model.Buyer;
import javax.validation.Valid;
import java.math.BigDecimal;
import javax.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import javax.annotation.Generated;

/**
 * CheckoutRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-20T16:30:25.909637+06:00[Asia/Bishkek]")
public class CheckoutRequest {

  private String partnerId;

  private String partnerPaymentId;

  private BigDecimal amount;

  private String currency;

  private Integer installmentDurationMonths;

  private Buyer buyer;

  /**
   * Default constructor
   * @deprecated Use {@link CheckoutRequest#CheckoutRequest(String, BigDecimal, String, Integer, Buyer)}
   */
  @Deprecated
  public CheckoutRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CheckoutRequest(String partnerId, String partnerPaymentId, BigDecimal amount, String currency, Integer installmentDurationMonths, Buyer buyer) {
    this.partnerPaymentId = partnerPaymentId;
    this.amount = amount;
    this.currency = currency;
    this.installmentDurationMonths = installmentDurationMonths;
    this.buyer = buyer;
  }

  public CheckoutRequest partnerId(String partnerId) {
    this.partnerId = partnerId;
    return this;
  }

  /**
   * Partner’s identifier to correlate with their platform
   * @return partnerId
  */
  @NotNull 
  @Schema(name = "partnerId", description = "Partner’s identifier to correlate with their platform", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("partnerId")
  public String getPartnerId() {
    return partnerId;
  }

  public void setPartnerId(String partnerId) {
    this.partnerId = partnerId;
  }

  public CheckoutRequest partnerPaymentId(String partnerPaymentId) {
    this.partnerPaymentId = partnerPaymentId;
    return this;
  }

  /**
   * Partner’s payment identifier to correlate with their platform
   * @return partnerPaymentId
  */
  @NotNull 
  @Schema(name = "partnerPaymentId", description = "Partner’s payment identifier to correlate with their platform", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("partnerPaymentId")
  public String getPartnerPaymentId() {
    return partnerPaymentId;
  }

  public void setPartnerPaymentId(String partnerPaymentId) {
    this.partnerPaymentId = partnerPaymentId;
  }

  public CheckoutRequest amount(BigDecimal amount) {
    this.amount = amount;
    return this;
  }

  /**
   * Get amount
   * @return amount
  */
  @NotNull 
  @javax.validation.constraints.Digits(integer = 16, fraction = 2)
  @Schema(name = "amount", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("amount")
  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public CheckoutRequest currency(String currency) {
    this.currency = currency;
    return this;
  }

  /**
   * Get currency
   * @return currency
  */
  @NotNull @Pattern(regexp = "^[A-Z]{3}$") 
  @Schema(name = "currency", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("currency")
  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public CheckoutRequest installmentDurationMonths(Integer installmentDurationMonths) {
    this.installmentDurationMonths = installmentDurationMonths;
    return this;
  }

  /**
   * Get installmentDurationMonths
   * minimum: 1
   * @return installmentDurationMonths
  */
  @NotNull @Min(1) 
  @Schema(name = "installmentDurationMonths", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("installmentDurationMonths")
  public Integer getInstallmentDurationMonths() {
    return installmentDurationMonths;
  }

  public void setInstallmentDurationMonths(Integer installmentDurationMonths) {
    this.installmentDurationMonths = installmentDurationMonths;
  }

  public CheckoutRequest buyer(Buyer buyer) {
    this.buyer = buyer;
    return this;
  }

  /**
   * Get buyer
   * @return buyer
  */
  @NotNull @Valid 
  @Schema(name = "buyer", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("buyer")
  public Buyer getBuyer() {
    return buyer;
  }

  public void setBuyer(Buyer buyer) {
    this.buyer = buyer;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CheckoutRequest checkoutRequest = (CheckoutRequest) o;
    return Objects.equals(this.partnerPaymentId, checkoutRequest.partnerPaymentId) &&
        Objects.equals(this.amount, checkoutRequest.amount) &&
        Objects.equals(this.currency, checkoutRequest.currency) &&
        Objects.equals(this.installmentDurationMonths, checkoutRequest.installmentDurationMonths) &&
        Objects.equals(this.buyer, checkoutRequest.buyer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(partnerPaymentId, amount, currency, installmentDurationMonths, buyer);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CheckoutRequest {\n");
    sb.append("    partnerPaymentId: ").append(toIndentedString(partnerPaymentId)).append("\n");
    sb.append("    amount: ").append(toIndentedString(amount)).append("\n");
    sb.append("    currency: ").append(toIndentedString(currency)).append("\n");
    sb.append("    installmentDurationMonths: ").append(toIndentedString(installmentDurationMonths)).append("\n");
    sb.append("    buyer: ").append(toIndentedString(buyer)).append("\n");
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
