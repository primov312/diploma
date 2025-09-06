package com.rocketcredit.gateway.model;

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
 * Buyer
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-20T16:30:25.909637+06:00[Asia/Bishkek]")
public class Buyer {

  private String partnerUserId;

  private String name;

  private String email;

  private String cardToken;

  /**
   * Default constructor
   * @deprecated Use {@link Buyer#Buyer(String, String, String, PaymentMethod)}
   */
  @Deprecated
  public Buyer() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Buyer(String partnerUserId, String name, String email, String cardToken) {
    this.partnerUserId = partnerUserId;
    this.name = name;
    this.email = email;
    this.cardToken = cardToken;
  }

  public Buyer partnerUserId(String partnerUserId) {
    this.partnerUserId = partnerUserId;
    return this;
  }

  /**
   * Get partnerUserId
   * @return partnerUserId
  */
  @NotNull 
  @Schema(name = "partnerUserId", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("partnerUserId")
  public String getPartnerUserId() {
    return partnerUserId;
  }

  public void setPartnerUserId(String partnerUserId) {
    this.partnerUserId = partnerUserId;
  }

  public Buyer name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
  */
  @NotNull 
  @Schema(name = "name", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Buyer email(String email) {
    this.email = email;
    return this;
  }

  /**
   * Get email
   * @return email
  */
  @NotNull @javax.validation.constraints.Email
  @Schema(name = "email", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Buyer paymentMethod(String cardToken) {
    this.cardToken = cardToken;
    return this;
  }

  /**
   * Get cardToken
   * @return cardToken
  */
  @NotNull @Valid 
  @Schema(name = "cardToken", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("cardToken")
  public String getCardToken() {
    return cardToken;
  }

  public void setCardToken(String cardToken) {
    this.cardToken = cardToken;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Buyer buyer = (Buyer) o;
    return Objects.equals(this.partnerUserId, buyer.partnerUserId) &&
        Objects.equals(this.name, buyer.name) &&
        Objects.equals(this.email, buyer.email) &&
        Objects.equals(this.cardToken, buyer.cardToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(partnerUserId, name, email, cardToken);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Buyer {\n");
    sb.append("    partnerUserId: ").append(toIndentedString(partnerUserId)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    email: ").append(toIndentedString(email)).append("\n");
    sb.append("    cardToken: ").append(toIndentedString(cardToken)).append("\n");
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

