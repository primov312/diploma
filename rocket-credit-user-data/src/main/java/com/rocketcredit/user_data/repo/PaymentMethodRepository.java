package com.rocketcredit.user_data.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.rocketcredit.user_data.entity.PaymentMethodEntity;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity,Long> {
  List<PaymentMethodEntity> findByUserId(Long userId);
  Optional<PaymentMethodEntity> findByUserIdAndToken(Long userId, String token);
}