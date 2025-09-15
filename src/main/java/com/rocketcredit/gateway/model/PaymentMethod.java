package com.rocketcredit.gateway.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import javax.validation.Valid;
import javax.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import javax.annotation.Generated;

/**
 * Partner’s payment method summary/token (non-PCI)
 */

@Schema(name = "PaymentMethod", description = "Partner’s payment method summary/token (non-PCI)")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-20T16:30:25.909637+06:00[Asia/Bishkek]")
public class PaymentMethod {

  /**
   * Gets or Sets type
   */
  public enum TypeEnum {
    CARD("card"),
    
    WALLET("wallet"),
    
    BANK("bank");

    private String value;

    TypeEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static TypeEnum fromValue(String value) {
      for (TypeEnum b : TypeEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  private TypeEnum type;

  private String last4;

  private String token;

  public PaymentMethod type(TypeEnum type) {
    this.type = type;
    return this;
  }

  /**
   * Get type
   * @return type
  */
  
  @Schema(name = "type", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("type")
  public TypeEnum getType() {
    return type;
  }

  public void setType(TypeEnum type) {
    this.type = type;
  }

  public PaymentMethod last4(String last4) {
    this.last4 = last4;
    return this;
  }

  /**
   * Get last4
   * @return last4
  */
  
  @Schema(name = "last4", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("last4")
  public String getLast4() {
    return last4;
  }

  public void setLast4(String last4) {
    this.last4 = last4;
  }

  public PaymentMethod token(String token) {
    this.token = token;
    return this;
  }

  /**
   * PSP token; never raw PAN
   * @return token
  */
  
  @Schema(name = "token", description = "PSP token; never raw PAN", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("token")
  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PaymentMethod paymentMethod = (PaymentMethod) o;
    return Objects.equals(this.type, paymentMethod.type) &&
        Objects.equals(this.last4, paymentMethod.last4) &&
        Objects.equals(this.token, paymentMethod.token);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, last4, token);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PaymentMethod {\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    last4: ").append(toIndentedString(last4)).append("\n");
    sb.append("    token: ").append(toIndentedString(token)).append("\n");
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

