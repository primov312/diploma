package com.rocketcredit.userdata.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import javax.validation.Valid;
import javax.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import javax.annotation.Generated;

/**
 * CheckoutRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-06-13T17:05:17.458467+02:00[Europe/Budapest]")
public class CheckoutRequest {

  private Integer userId;

  private Double cartTotal;

  /**
   * Default constructor
   * @deprecated Use {@link CheckoutRequest#CheckoutRequest(Integer, Double)}
   */
  @Deprecated
  public CheckoutRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CheckoutRequest(Integer userId, Double cartTotal) {
    this.userId = userId;
    this.cartTotal = cartTotal;
  }

  public CheckoutRequest userId(Integer userId) {
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
  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public CheckoutRequest cartTotal(Double cartTotal) {
    this.cartTotal = cartTotal;
    return this;
  }

  /**
   * Get cartTotal
   * @return cartTotal
  */
  @NotNull 
  @Schema(name = "cartTotal", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("cartTotal")
  public Double getCartTotal() {
    return cartTotal;
  }

  public void setCartTotal(Double cartTotal) {
    this.cartTotal = cartTotal;
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
    return Objects.equals(this.userId, checkoutRequest.userId) &&
        Objects.equals(this.cartTotal, checkoutRequest.cartTotal);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, cartTotal);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CheckoutRequest {\n");
    sb.append("    userId: ").append(toIndentedString(userId)).append("\n");
    sb.append("    cartTotal: ").append(toIndentedString(cartTotal)).append("\n");
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

