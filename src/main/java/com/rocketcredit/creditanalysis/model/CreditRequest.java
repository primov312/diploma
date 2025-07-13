package com.rocketcredit.creditanalysis.model;

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
 * CreditRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-07-13T13:24:23.415761+02:00[Europe/Budapest]")
public class CreditRequest {

  private Long userId;

  private Double cartTotal;

  /**
   * Default constructor
   * @deprecated Use {@link CreditRequest#CreditRequest(Long, Double)}
   */
  @Deprecated
  public CreditRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CreditRequest(Long userId, Double cartTotal) {
    this.userId = userId;
    this.cartTotal = cartTotal;
  }

  public CreditRequest userId(Long userId) {
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

  public CreditRequest cartTotal(Double cartTotal) {
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
    CreditRequest creditRequest = (CreditRequest) o;
    return Objects.equals(this.userId, creditRequest.userId) &&
        Objects.equals(this.cartTotal, creditRequest.cartTotal);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, cartTotal);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreditRequest {\n");
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

