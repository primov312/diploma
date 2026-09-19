package com.rocketcredit.backend.applications;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One saved credit decision: the request, the feature snapshot sent to
 * analysis, the complete result and the policy/model versions that produced
 * it. Written in one transaction; read only by its owner.
 */
@Entity
@Table(name = "credit_applications")
public class CreditApplicationEntity {
    public enum DecisionStatus { APPROVED, REJECTED, REVIEW }
    public enum AiStatus { NOT_REQUESTED, UNAVAILABLE, APPLIED }
    public enum PreparationMode { SEQUENTIAL, PARALLEL }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "requested_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false, length = 10)
    private DecisionStatus decisionStatus;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal score;

    @Column(name = "possible_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal possibleAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> reasons;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> factors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> featureSnapshot;

    @Column(name = "ai_requested", nullable = false)
    private boolean aiRequested;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", nullable = false, length = 20)
    private AiStatus aiStatus;

    @Column(name = "policy_version", nullable = false, length = 40)
    private String policyVersion;

    @Column(name = "model_version", length = 40)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "preparation_mode", nullable = false, length = 10)
    private PreparationMode preparationMode = PreparationMode.SEQUENTIAL;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected CreditApplicationEntity() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getPartnerId() { return partnerId; }
    public Long getProductId() { return productId; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public String getCurrency() { return currency; }
    public DecisionStatus getDecisionStatus() { return decisionStatus; }
    public BigDecimal getScore() { return score; }
    public BigDecimal getPossibleAmount() { return possibleAmount; }
    public List<String> getReasons() { return reasons; }
    public Map<String, Object> getFactors() { return factors; }
    public Map<String, Object> getFeatureSnapshot() { return featureSnapshot; }
    public boolean isAiRequested() { return aiRequested; }
    public AiStatus getAiStatus() { return aiStatus; }
    public String getPolicyVersion() { return policyVersion; }
    public String getModelVersion() { return modelVersion; }
    public PreparationMode getPreparationMode() { return preparationMode; }
    public OffsetDateTime getObservedAt() { return observedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    public void setDecisionStatus(DecisionStatus decisionStatus) { this.decisionStatus = decisionStatus; }
    public void setScore(BigDecimal score) { this.score = score; }
    public void setPossibleAmount(BigDecimal possibleAmount) { this.possibleAmount = possibleAmount; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }
    public void setFactors(Map<String, Object> factors) { this.factors = factors; }
    public void setFeatureSnapshot(Map<String, Object> featureSnapshot) { this.featureSnapshot = featureSnapshot; }
    public void setAiRequested(boolean aiRequested) { this.aiRequested = aiRequested; }
    public void setAiStatus(AiStatus aiStatus) { this.aiStatus = aiStatus; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public void setPreparationMode(PreparationMode preparationMode) { this.preparationMode = preparationMode; }
    public void setObservedAt(OffsetDateTime observedAt) { this.observedAt = observedAt; }
}
