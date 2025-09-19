package com.rocketcredit.gateway.api;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rocketcredit.gateway.model.Installment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.openapitools.jackson.nullable.JsonNullable;
import javax.validation.Valid;
import io.swagger.v3.oas.annotations.media.Schema;


import javax.annotation.Generated;

/**
 * CheckoutResponse
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-08-20T16:30:25.909637+06:00[Asia/Bishkek]")
public class CheckoutResponse {

  private Boolean approved;

  private JsonNullable<String> reason = JsonNullable.undefined();

  private Long userId;

  private String paymentId;

  private String partnerPaymentId;

  private String repaymentPlanId;

  private Integer installmentDurationMonths;

  @Valid
  private List<@Valid Installment> schedule;

  public CheckoutResponse approved(Boolean approved) {
    this.approved = approved;
    return this;
  }

  /**
   * Get approved
   * @return approved
  */
  
  @Schema(name = "approved", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("approved")
  public Boolean getApproved() {
    return approved;
  }

  public void setApproved(Boolean approved) {
    this.approved = approved;
  }

  public CheckoutResponse reason(String reason) {
    this.reason = JsonNullable.of(reason);
    return this;
  }

  /**
   * Get reason
   * @return reason
  */
  
  @Schema(name = "reason", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("reason")
  public JsonNullable<String> getReason() {
    return reason;
  }

  public void setReason(JsonNullable<String> reason) {
    this.reason = reason;
  }

  public CheckoutResponse userId(Long userId) {
    this.userId = userId;
    return this;
  }

  /**
   * Get userId
   * @return userId
  */
  
  @Schema(name = "userId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("userId")
  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public CheckoutResponse paymentId(String paymentId) {
    this.paymentId = paymentId;
    return this;
  }

  /**
   * Internal payment id
   * @return paymentId
  */
  
  @Schema(name = "paymentId", description = "Internal payment id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("paymentId")
  public String getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(String paymentId) {
    this.paymentId = paymentId;
  }

  public CheckoutResponse partnerPaymentId(String partnerPaymentId) {
    this.partnerPaymentId = partnerPaymentId;
    return this;
  }

  /**
   * Get partnerPaymentId
   * @return partnerPaymentId
  */
  
  @Schema(name = "partnerPaymentId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("partnerPaymentId")
  public String getPartnerPaymentId() {
    return partnerPaymentId;
  }

  public void setPartnerPaymentId(String partnerPaymentId) {
    this.partnerPaymentId = partnerPaymentId;
  }

  public CheckoutResponse repaymentPlanId(String repaymentPlanId) {
    this.repaymentPlanId = repaymentPlanId;
    return this;
  }

  /**
   * Get repaymentPlanId
   * @return repaymentPlanId
  */
  
  @Schema(name = "repaymentPlanId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("repaymentPlanId")
  public String getRepaymentPlanId() {
    return repaymentPlanId;
  }

  public void setRepaymentPlanId(String repaymentPlanId) {
    this.repaymentPlanId = repaymentPlanId;
  }

  public CheckoutResponse installmentDurationMonths(Integer installmentDurationMonths) {
    this.installmentDurationMonths = installmentDurationMonths;
    return this;
  }

  /**
   * Get installmentDurationMonths
   * @return installmentDurationMonths
  */
  
  @Schema(name = "installmentDurationMonths", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("installmentDurationMonths")
  public Integer getInstallmentDurationMonths() {
    return installmentDurationMonths;
  }

  public void setInstallmentDurationMonths(Integer installmentDurationMonths) {
    this.installmentDurationMonths = installmentDurationMonths;
  }

  public CheckoutResponse schedule(List<@Valid Installment> schedule) {
    this.schedule = schedule;
    return this;
  }

  public CheckoutResponse addScheduleItem(Installment scheduleItem) {
    if (this.schedule == null) {
      this.schedule = new ArrayList<>();
    }
    this.schedule.add(scheduleItem);
    return this;
  }

  /**
   * Get schedule
   * @return schedule
  */
  @Valid 
  @Schema(name = "schedule", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("schedule")
  public List<@Valid Installment> getSchedule() {
    return schedule;
  }

  public void setSchedule(List<@Valid Installment> schedule) {
    this.schedule = schedule;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CheckoutResponse checkoutResponse = (CheckoutResponse) o;
    return Objects.equals(this.approved, checkoutResponse.approved) &&
        equalsNullable(this.reason, checkoutResponse.reason) &&
        Objects.equals(this.userId, checkoutResponse.userId) &&
        Objects.equals(this.paymentId, checkoutResponse.paymentId) &&
        Objects.equals(this.partnerPaymentId, checkoutResponse.partnerPaymentId) &&
        Objects.equals(this.repaymentPlanId, checkoutResponse.repaymentPlanId) &&
        Objects.equals(this.installmentDurationMonths, checkoutResponse.installmentDurationMonths) &&
        Objects.equals(this.schedule, checkoutResponse.schedule);
  }

  private static <T> boolean equalsNullable(JsonNullable<T> a, JsonNullable<T> b) {
    return a == b || (a != null && b != null && a.isPresent() && b.isPresent() && Objects.deepEquals(a.get(), b.get()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(approved, hashCodeNullable(reason), userId, paymentId, partnerPaymentId, repaymentPlanId, installmentDurationMonths, schedule);
  }

  private static <T> int hashCodeNullable(JsonNullable<T> a) {
    if (a == null) {
      return 1;
    }
    return a.isPresent() ? Arrays.deepHashCode(new Object[]{a.get()}) : 31;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CheckoutResponse {\n");
    sb.append("    approved: ").append(toIndentedString(approved)).append("\n");
    sb.append("    reason: ").append(toIndentedString(reason)).append("\n");
    sb.append("    userId: ").append(toIndentedString(userId)).append("\n");
    sb.append("    paymentId: ").append(toIndentedString(paymentId)).append("\n");
    sb.append("    partnerPaymentId: ").append(toIndentedString(partnerPaymentId)).append("\n");
    sb.append("    repaymentPlanId: ").append(toIndentedString(repaymentPlanId)).append("\n");
    sb.append("    installmentDurationMonths: ").append(toIndentedString(installmentDurationMonths)).append("\n");
    sb.append("    schedule: ").append(toIndentedString(schedule)).append("\n");
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
