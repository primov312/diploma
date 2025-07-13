package com.rocketcredit.userdata.repo;

import com.rocketcredit.userdata.entity.PaymentMethodEntity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity,Long> {
  List<PaymentMethodEntity> findByUserId(Long userId);
}