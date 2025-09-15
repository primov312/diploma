package com.rocketcredit.payment.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Payee
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-23T23:31:22.594277+06:00[Asia/Bishkek]")
public class Payee {

  private String name;

  private String partnerUserId;

  private String email;

  /**
   * Default constructor
   */
  @Deprecated
  public Payee() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Payee(String name, String partnerUserId, String email) {
    this.name = name;
    this.partnerUserId = partnerUserId;
    this.email = email;
  }

  public Payee name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Beneficiary name
   * @return name
  */
  
  @Schema(name = "name", description = "Beneficiary name", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * Beneficiary partnerUserId
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

  /**
   * Beneficiary email
   * @return email
  */
  
  @Schema(name = "email", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Payee payee = (Payee) o;
    return Objects.equals(this.name, payee.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash( name);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Payee {\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    email: ").append(toIndentedString(email)).append("\n");
    sb.append("    partnerUserId: ").append(toIndentedString(partnerUserId)).append("\n");
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

