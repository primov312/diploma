package com.rocketcredit.user_data.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Map;

import org.hibernate.annotations.Type;

import com.vladmihalcea.hibernate.type.json.JsonType;

@Entity
@Table(name = "user_stats")
public class UserStatsEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt = OffsetDateTime.now();

    @Column(name = "kyc_passed")
    private Boolean kyc_passed;

    @Column(name = "partner_orders_12m")
    private Integer partner_orders_12m;

    @Column(name = "partner_avg_order_value")
    private Double  partner_avg_order_value;

    @Column(name = "partner_refund_rate")
    private Double  partner_refund_rate;

    @Column(name = "partner_ontime_ratio")
    private Double  partner_ontime_ratio;

    @Column(name = "partner_tenure_months")
    private Integer partner_tenure_months;

    @Column(name = "rocket_ontime_ratio")
    private Double  rocket_ontime_ratio;

    @Column(name = "rocket_dpd30_12m")
    private Integer rocket_dpd30_12m;

    @Column(name = "rocket_active_plans")
    private Integer rocket_active_plans;

    @Column(name = "rocket_tenure_months")
    private Integer rocket_tenure_months;

    @Column(name = "credit_limit")
    private Double credit_limit;

    @Column(name = "income")
    private Double income;

    @Column(name = "credit_bureau_score")
    private Integer creditBureauScore = 650;

    @Column(name = "social_consent")
    private Boolean socialConsent = false;

    @Type(JsonType.class)
    @Column(name = "social_handles", columnDefinition = "jsonb")
    private Map<String, Object> socialHandles;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public OffsetDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(OffsetDateTime computedAt) {
        this.computedAt = computedAt;
    }

    public Boolean isKyc_passed() {
        return kyc_passed;
    }

    public void setKyc_passed(Boolean kyc_passed) {
        this.kyc_passed = kyc_passed;
    }

    public Integer getPartner_orders_12m() {
        return partner_orders_12m;
    }

    public void setPartner_orders_12m(Integer partner_orders_12m) {
        this.partner_orders_12m = partner_orders_12m;
    }

    public Double getPartner_avg_order_value() {
        return partner_avg_order_value;
    }

    public void setPartner_avg_order_value(Double partner_avg_order_value) {
        this.partner_avg_order_value = partner_avg_order_value;
    }

    public Double getPartner_refund_rate() {
        return partner_refund_rate;
    }

    public void setPartner_refund_rate(Double partner_refund_rate) {
        this.partner_refund_rate = partner_refund_rate;
    }

    public Double getPartner_ontime_ratio() {
        return partner_ontime_ratio;
    }

    public void setPartner_ontime_ratio(Double partner_ontime_ratio) {
        this.partner_ontime_ratio = partner_ontime_ratio;
    }

    public Integer getPartner_tenure_months() {
        return partner_tenure_months;
    }

    public void setPartner_tenure_months(Integer partner_tenure_months) {
        this.partner_tenure_months = partner_tenure_months;
    }

    public Double getRocket_ontime_ratio() {
        return rocket_ontime_ratio;
    }

    public void setRocket_ontime_ratio(Double rocket_ontime_ratio) {
        this.rocket_ontime_ratio = rocket_ontime_ratio;
    }

    public Integer getRocket_dpd30_12m() {
        return rocket_dpd30_12m;
    }

    public void setRocket_dpd30_12m(Integer rocket_dpd30_12m) {
        this.rocket_dpd30_12m = rocket_dpd30_12m;
    }

    public Integer getRocket_active_plans() {
        return rocket_active_plans;
    }

    public void setRocket_active_plans(Integer rocket_active_plans) {
        this.rocket_active_plans = rocket_active_plans;
    }

    public Integer getRocket_tenure_months() {
        return rocket_tenure_months;
    }

    public void setRocket_tenure_months(Integer rocket_tenure_months) {
        this.rocket_tenure_months = rocket_tenure_months;
    }

    public Double getIncome() {
        return income;
    }

    public void setIncome(Double income) {
        this.income = income;
    }

    public Double getCredit_limit() {
        return credit_limit;
    }

    public void setCredit_limit(Double credit_limit) {
        this.credit_limit = credit_limit;
    }
}
