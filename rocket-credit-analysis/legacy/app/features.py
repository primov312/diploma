FEATURE_TO_REASON = {
    "partner_orders_12m":      PARTNER_TOO_FEW_ORDERS,
    "partner_ontime_ratio":    PARTNER_ON_TIME_LOW,
    "partner_refund_rate":     PARTNER_REFUND_RATE_HIGH,
    "rocket_dpd30_12m":        ROCKET_RECENT_LATE_PAYMENT,
    "rocket_active_plans":     ROCKET_TOO_MANY_ACTIVE_PLANS,
    "bureau_delinquency_flag": BUREAU_RECENT_DELINQUENCY,
    "bureau_utilization":      BUREAU_UTILIZATION_HIGH,
    "bureau_inquiries_6m":     BUREAU_INQUIRIES_HIGH,
    "social_account_age_m":    SOCIAL_ACCOUNT_RECENCY_LOW,
    "social_volatility":       SOCIAL_NETWORK_VOLATILE,
}