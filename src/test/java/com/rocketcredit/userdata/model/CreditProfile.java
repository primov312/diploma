package com.rocketcredit.userdata.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.rocketcredit.userdata.model.SocialInsights;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import javax.validation.Valid;
import javax.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import javax.annotation.Generated;

/**
 * Aggregated credit profile including derived social insights
 */

@Schema(name = "CreditProfile", description = "Aggregated credit profile including derived social insights")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-07-27T11:24:20.422539+02:00[Europe/Budapest]")
public class CreditProfile {

  private Long userId;

  private Double annualIncome;

  private Integer creditBureauScore;

  private Integer numTransactions;

  private Double totalSpent;

  private Double avgTransactionAmount;

  private Double onTimePaymentRate;

  private Integer numPaymentMethods;

  private Integer numActiveBnpl;

  private SocialInsights socialInsights;

  public CreditProfile userId(Long userId) {
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

  public CreditProfile annualIncome(Double annualIncome) {
    this.annualIncome = annualIncome;
    return this;
  }

  /**
   * Get annualIncome
   * @return annualIncome
  */
  
  @Schema(name = "annualIncome", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("annualIncome")
  public Double getAnnualIncome() {
    return annualIncome;
  }

  public void setAnnualIncome(Double annualIncome) {
    this.annualIncome = annualIncome;
  }

  public CreditProfile creditBureauScore(Integer creditBureauScore) {
    this.creditBureauScore = creditBureauScore;
    return this;
  }

  /**
   * Get creditBureauScore
   * @return creditBureauScore
  */
  
  @Schema(name = "creditBureauScore", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("creditBureauScore")
  public Integer getCreditBureauScore() {
    return creditBureauScore;
  }

  public void setCreditBureauScore(Integer creditBureauScore) {
    this.creditBureauScore = creditBureauScore;
  }

  public CreditProfile numTransactions(Integer numTransactions) {
    this.numTransactions = numTransactions;
    return this;
  }

  /**
   * Get numTransactions
   * @return numTransactions
  */
  
  @Schema(name = "numTransactions", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("numTransactions")
  public Integer getNumTransactions() {
    return numTransactions;
  }

  public void setNumTransactions(Integer numTransactions) {
    this.numTransactions = numTransactions;
  }

  public CreditProfile totalSpent(Double totalSpent) {
    this.totalSpent = totalSpent;
    return this;
  }

  /**
   * Get totalSpent
   * @return totalSpent
  */
  
  @Schema(name = "totalSpent", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("totalSpent")
  public Double getTotalSpent() {
    return totalSpent;
  }

  public void setTotalSpent(Double totalSpent) {
    this.totalSpent = totalSpent;
  }

  public CreditProfile avgTransactionAmount(Double avgTransactionAmount) {
    this.avgTransactionAmount = avgTransactionAmount;
    return this;
  }

  /**
   * Get avgTransactionAmount
   * @return avgTransactionAmount
  */
  
  @Schema(name = "avgTransactionAmount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("avgTransactionAmount")
  public Double getAvgTransactionAmount() {
    return avgTransactionAmount;
  }

  public void setAvgTransactionAmount(Double avgTransactionAmount) {
    this.avgTransactionAmount = avgTransactionAmount;
  }

  public CreditProfile onTimePaymentRate(Double onTimePaymentRate) {
    this.onTimePaymentRate = onTimePaymentRate;
    return this;
  }

  /**
   * Derived proxy for on-time payments (0 - 1)
   * @return onTimePaymentRate
  */
  
  @Schema(name = "onTimePaymentRate", description = "Derived proxy for on-time payments (0 - 1)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("onTimePaymentRate")
  public Double getOnTimePaymentRate() {
    return onTimePaymentRate;
  }

  public void setOnTimePaymentRate(Double onTimePaymentRate) {
    this.onTimePaymentRate = onTimePaymentRate;
  }

  public CreditProfile numPaymentMethods(Integer numPaymentMethods) {
    this.numPaymentMethods = numPaymentMethods;
    return this;
  }

  /**
   * Get numPaymentMethods
   * @return numPaymentMethods
  */
  
  @Schema(name = "numPaymentMethods", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("numPaymentMethods")
  public Integer getNumPaymentMethods() {
    return numPaymentMethods;
  }

  public void setNumPaymentMethods(Integer numPaymentMethods) {
    this.numPaymentMethods = numPaymentMethods;
  }

  public CreditProfile numActiveBnpl(Integer numActiveBnpl) {
    this.numActiveBnpl = numActiveBnpl;
    return this;
  }

  /**
   * Number of active BNPL plans
   * @return numActiveBnpl
  */
  
  @Schema(name = "numActiveBnpl", description = "Number of active BNPL plans", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("numActiveBnpl")
  public Integer getNumActiveBnpl() {
    return numActiveBnpl;
  }

  public void setNumActiveBnpl(Integer numActiveBnpl) {
    this.numActiveBnpl = numActiveBnpl;
  }

  public CreditProfile socialInsights(SocialInsights socialInsights) {
    this.socialInsights = socialInsights;
    return this;
  }

  /**
   * Get socialInsights
   * @return socialInsights
  */
  @Valid 
  @Schema(name = "socialInsights", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("socialInsights")
  public SocialInsights getSocialInsights() {
    return socialInsights;
  }

  public void setSocialInsights(SocialInsights socialInsights) {
    this.socialInsights = socialInsights;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CreditProfile creditProfile = (CreditProfile) o;
    return Objects.equals(this.userId, creditProfile.userId) &&
        Objects.equals(this.annualIncome, creditProfile.annualIncome) &&
        Objects.equals(this.creditBureauScore, creditProfile.creditBureauScore) &&
        Objects.equals(this.numTransactions, creditProfile.numTransactions) &&
        Objects.equals(this.totalSpent, creditProfile.totalSpent) &&
        Objects.equals(this.avgTransactionAmount, creditProfile.avgTransactionAmount) &&
        Objects.equals(this.onTimePaymentRate, creditProfile.onTimePaymentRate) &&
        Objects.equals(this.numPaymentMethods, creditProfile.numPaymentMethods) &&
        Objects.equals(this.numActiveBnpl, creditProfile.numActiveBnpl) &&
        Objects.equals(this.socialInsights, creditProfile.socialInsights);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, annualIncome, creditBureauScore, numTransactions, totalSpent, avgTransactionAmount, onTimePaymentRate, numPaymentMethods, numActiveBnpl, socialInsights);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreditProfile {\n");
    sb.append("    userId: ").append(toIndentedString(userId)).append("\n");
    sb.append("    annualIncome: ").append(toIndentedString(annualIncome)).append("\n");
    sb.append("    creditBureauScore: ").append(toIndentedString(creditBureauScore)).append("\n");
    sb.append("    numTransactions: ").append(toIndentedString(numTransactions)).append("\n");
    sb.append("    totalSpent: ").append(toIndentedString(totalSpent)).append("\n");
    sb.append("    avgTransactionAmount: ").append(toIndentedString(avgTransactionAmount)).append("\n");
    sb.append("    onTimePaymentRate: ").append(toIndentedString(onTimePaymentRate)).append("\n");
    sb.append("    numPaymentMethods: ").append(toIndentedString(numPaymentMethods)).append("\n");
    sb.append("    numActiveBnpl: ").append(toIndentedString(numActiveBnpl)).append("\n");
    sb.append("    socialInsights: ").append(toIndentedString(socialInsights)).append("\n");
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

