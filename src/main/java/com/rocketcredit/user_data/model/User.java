package com.rocketcredit.user_data.model;

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
 * User
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-20T17:02:00.623423+06:00[Asia/Bishkek]")
public class User {

  private Long id;

  private String partnerUserId;

  private String name;

  private String email;

  /**
   * Default constructor
   * @deprecated Use {@link User#User(Long, String, String)}
   */
  @Deprecated
  public User() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public User(Long id, String name, String email) {
    this.id = id;
    this.name = name;
    this.email = email;
  }

  public User id(Long id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
  */
  @NotNull 
  @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User partnerUserId(String partnerUserId) {
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

  public User name(String name) {
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

  public User email(String email) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    User user = (User) o;
    return Objects.equals(this.id, user.id) &&
        Objects.equals(this.partnerUserId, user.partnerUserId) &&
        Objects.equals(this.name, user.name) &&
        Objects.equals(this.email, user.email);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, partnerUserId, name, email);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class User {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    partnerUserId: ").append(toIndentedString(partnerUserId)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    email: ").append(toIndentedString(email)).append("\n");
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

