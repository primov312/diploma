package com.rocketcredit.user_data.model;

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

  public UserResolveRequest partnerUserId(String partnerUserId) {
    this.partnerUserId = partnerUserId;
    return this;
  }

  /**
   * Get partnerUserId
   * @return partnerUserId
  */
  
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

  /**
   * Get email
   * @return email
  */
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

  /**
   * Get name
   * @return name
  */
  
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

  /**
   * Get paymentMethod
   * @return paymentMethod
  */
  @Valid 
  @Schema(name = "paymentMethod", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("paymentMethod")
  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(PaymentMethod paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UserResolveRequest userResolveRequest = (UserResolveRequest) o;
    return Objects.equals(this.partnerUserId, userResolveRequest.partnerUserId) &&
        Objects.equals(this.email, userResolveRequest.email) &&
        Objects.equals(this.name, userResolveRequest.name) &&
        Objects.equals(this.paymentMethod, userResolveRequest.paymentMethod);
  }

  @Override
  public int hashCode() {
    return Objects.hash(partnerUserId, email, name, paymentMethod);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class UserResolveRequest {\n");
    sb.append("    partnerUserId: ").append(toIndentedString(partnerUserId)).append("\n");
    sb.append("    email: ").append(toIndentedString(email)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    paymentMethod: ").append(toIndentedString(paymentMethod)).append("\n");
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

