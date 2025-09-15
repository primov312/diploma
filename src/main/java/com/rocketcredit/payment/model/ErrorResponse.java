package com.rocketcredit.payment.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.HashMap;
import java.util.Map;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * ErrorResponse
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-23T23:31:22.594277+06:00[Asia/Bishkek]")
public class ErrorResponse {

  private String code;

  private String message;

  @Valid
  private Map<String, Object> details = new HashMap<>();

  /**
   * Default constructor
   * @deprecated Use {@link ErrorResponse#ErrorResponse(String, String)}
   */
  @Deprecated
  public ErrorResponse() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public ErrorResponse(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public ErrorResponse code(String code) {
    this.code = code;
    return this;
  }

  /**
   * Get code
   * @return code
  */
  @NotNull 
  @Schema(name = "code", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("code")
  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public ErrorResponse message(String message) {
    this.message = message;
    return this;
  }

  /**
   * Get message
   * @return message
  */
  @NotNull 
  @Schema(name = "message", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("message")
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public ErrorResponse details(Map<String, Object> details) {
    this.details = details;
    return this;
  }

  public ErrorResponse putDetailsItem(String key, Object detailsItem) {
    if (this.details == null) {
      this.details = new HashMap<>();
    }
    this.details.put(key, detailsItem);
    return this;
  }

  /**
   * Get details
   * @return details
  */
  
  @Schema(name = "details", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("details")
  public Map<String, Object> getDetails() {
    return details;
  }

  public void setDetails(Map<String, Object> details) {
    this.details = details;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ErrorResponse errorResponse = (ErrorResponse) o;
    return Objects.equals(this.code, errorResponse.code) &&
        Objects.equals(this.message, errorResponse.message) &&
        Objects.equals(this.details, errorResponse.details);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, details);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ErrorResponse {\n");
    sb.append("    code: ").append(toIndentedString(code)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    details: ").append(toIndentedString(details)).append("\n");
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

