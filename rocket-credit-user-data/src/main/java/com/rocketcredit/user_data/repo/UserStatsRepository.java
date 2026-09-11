package com.rocketcredit.user_data.repo;

import com.rocketcredit.user_data.entity.UserStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Repository
public interface UserStatsRepository extends JpaRepository<UserStatsEntity, Long> {

  @NonNull
  Optional<UserStatsEntity> findByUserId(@NonNull Long userId);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      INSERT INTO user_stats (
        user_id, computed_at,
        kyc_passed,
        partner_orders_12m, partner_avg_order_value, partner_refund_rate, partner_ontime_ratio, partner_tenure_months,
        rocket_ontime_ratio, rocket_dpd30_12m, rocket_active_plans, rocket_tenure_months,
        income, credit_limit
      ) VALUES (
        :userId, now(),
        :kycPassed,
        :pOrders12m, :pAov, :pRefund, :pOnTime, :pTenure,
        :rOnTime, :rDpd30, :rActive, :rTenure,
        :income, :creditLimit
      )
      ON CONFLICT (user_id) DO UPDATE SET
        computed_at = excluded.computed_at,
        kyc_passed = excluded.kyc_passed,
        partner_orders_12m = excluded.partner_orders_12m,
        partner_avg_order_value = excluded.partner_avg_order_value,
        partner_refund_rate = excluded.partner_refund_rate,
        partner_ontime_ratio = excluded.partner_ontime_ratio,
        partner_tenure_months = excluded.partner_tenure_months,
        rocket_ontime_ratio = excluded.rocket_ontime_ratio,
        rocket_dpd30_12m = excluded.rocket_dpd30_12m,
        rocket_active_plans = excluded.rocket_active_plans,
        rocket_tenure_months = excluded.rocket_tenure_months,
        income = excluded.income,
        credit_limit = excluded.credit_limit
      """, nativeQuery = true)
  int upsert(
      @Param("userId") Long userId,
      @Param("kycPassed") boolean kycPassed,
      @Param("pOrders12m") int pOrders12m,
      @Param("pAov") double pAov,
      @Param("pRefund") double pRefund,
      @Param("pOnTime") double pOnTime,
      @Param("pTenure") int pTenure,
      @Param("rOnTime") double rOnTime,
      @Param("rDpd30") int rDpd30,
      @Param("rActive") int rActive,
      @Param("rTenure") int rTenure,
      @Param("income") double income,
      @Param("creditLimit") double creditLimit
  );

  @Transactional
  default int upsertFromFeatures(Long userId, Map<String, Object> f) {
    return upsert(
        userId,
        (boolean) f.getOrDefault("kyc_passed", Boolean.TRUE),
        ((Number) f.getOrDefault("partner_orders_12m", 0)).intValue(),
        ((Number) f.getOrDefault("partner_avg_order_value", 0.0)).doubleValue(),
        ((Number) f.getOrDefault("partner_refund_rate", 0.0)).doubleValue(),
        ((Number) f.getOrDefault("partner_ontime_ratio", 0.0)).doubleValue(),
        ((Number) f.getOrDefault("partner_tenure_months", 0)).intValue(),
        ((Number) f.getOrDefault("rocket_ontime_ratio", 0.0)).doubleValue(),
        ((Number) f.getOrDefault("rocket_dpd30_12m", 0)).intValue(),
        ((Number) f.getOrDefault("rocket_active_plans", 0)).intValue(),
        ((Number) f.getOrDefault("rocket_tenure_months", 0)).intValue(),
        ((Number) f.getOrDefault("income", 0.0)).doubleValue(),
        ((Number) f.getOrDefault("credit_limit", 0.0)).doubleValue()
    );
  }
}