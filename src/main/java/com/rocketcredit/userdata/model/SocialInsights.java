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
 * Basic social media insights
 */

@Schema(name = "SocialInsights", description = "Basic social media insights")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-07-27T11:24:20.422539+02:00[Europe/Budapest]")
public class SocialInsights {

  private Integer jobStabilityScore;

  private Integer networkQuality;

  private Double activitySentiment;

  public SocialInsights jobStabilityScore(Integer jobStabilityScore) {
    this.jobStabilityScore = jobStabilityScore;
    return this;
  }

  /**
   * Derived from LinkedIn (mock 0 - 100)
   * @return jobStabilityScore
  */
  
  @Schema(name = "jobStabilityScore", description = "Derived from LinkedIn (mock 0 - 100)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("jobStabilityScore")
  public Integer getJobStabilityScore() {
    return jobStabilityScore;
  }

  public void setJobStabilityScore(Integer jobStabilityScore) {
    this.jobStabilityScore = jobStabilityScore;
  }

  public SocialInsights networkQuality(Integer networkQuality) {
    this.networkQuality = networkQuality;
    return this;
  }

  /**
   * Proxy for quality of connections
   * @return networkQuality
  */
  
  @Schema(name = "networkQuality", description = "Proxy for quality of connections", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("networkQuality")
  public Integer getNetworkQuality() {
    return networkQuality;
  }

  public void setNetworkQuality(Integer networkQuality) {
    this.networkQuality = networkQuality;
  }

  public SocialInsights activitySentiment(Double activitySentiment) {
    this.activitySentiment = activitySentiment;
    return this;
  }

  /**
   * Sentiment score of recent activity (0 - 1)
   * @return activitySentiment
  */
  
  @Schema(name = "activitySentiment", description = "Sentiment score of recent activity (0 - 1)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("activitySentiment")
  public Double getActivitySentiment() {
    return activitySentiment;
  }

  public void setActivitySentiment(Double activitySentiment) {
    this.activitySentiment = activitySentiment;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SocialInsights socialInsights = (SocialInsights) o;
    return Objects.equals(this.jobStabilityScore, socialInsights.jobStabilityScore) &&
        Objects.equals(this.networkQuality, socialInsights.networkQuality) &&
        Objects.equals(this.activitySentiment, socialInsights.activitySentiment);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobStabilityScore, networkQuality, activitySentiment);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SocialInsights {\n");
    sb.append("    jobStabilityScore: ").append(toIndentedString(jobStabilityScore)).append("\n");
    sb.append("    networkQuality: ").append(toIndentedString(networkQuality)).append("\n");
    sb.append("    activitySentiment: ").append(toIndentedString(activitySentiment)).append("\n");
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

