package com.rocketcredit.user_data.model;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.annotation.Generated;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UserResolveRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-20T17:02:00.623423+06:00[Asia/Bishkek]")
public class UserResolveRequest {

  private String partnerUserId;
  private String email;
  private String name;
  private PaymentMethod paymentMethod;

  @Valid
  private List<@Valid Transaction> transactions;

  public UserResolveRequest partnerUserId(String partnerUserId) {
    this.partnerUserId = partnerUserId;
    return this;
  }

  @Schema(name = "partnerUserId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("partnerUserId")
  public String getPartnerUserId() {
    return partnerUserId;
  }

  public void setPartnerUserId(String partnerUserId) {
    this.partnerUserId = partnerUserId;
  }

  public UserResolveRequest email(String email) {
    this.email = email;
    return this;
  }

  @jakarta.validation.constraints.Email
  @Schema(name = "email", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public UserResolveRequest name(String name) {
    this.name = name;
    return this;
  }

  @Schema(name = "name", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public UserResolveRequest paymentMethod(PaymentMethod paymentMethod) {
    this.paymentMethod = paymentMethod;
    return this;
  }

  @Valid
  @Schema(name = "paymentMethod", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("paymentMethod")
  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(PaymentMethod paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  public UserResolveRequest transactions(List<Transaction> transactions) {
    this.transactions = transactions;
    return this;
  }

  @Valid
  @Schema(name = "transactions", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("transactions")
  public List<Transaction> getTransactions() {
    return transactions;
  }

  public void setTransactions(List<Transaction> transactions) {
    this.transactions = transactions;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UserResolveRequest that = (UserResolveRequest) o;
    return Objects.equals(partnerUserId, that.partnerUserId) &&
          Objects.equals(email, that.email) &&
          Objects.equals(name, that.name) &&
          Objects.equals(paymentMethod, that.paymentMethod) &&
          Objects.equals(transactions, that.transactions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(partnerUserId, email, name, paymentMethod, transactions);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class UserResolveRequest {\n");
    sb.append("    partnerUserId: ").append(toIndentedString(partnerUserId)).append("\n");
    sb.append("    email: ").append(toIndentedString(email)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    paymentMethod: ").append(toIndentedString(paymentMethod)).append("\n");
    sb.append("    transactions: ").append(toIndentedString(transactions)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  private String toIndentedString(Object o) {
    if (o == null) return "null";
    return o.toString().replace("\n", "\n    ");
  }
}