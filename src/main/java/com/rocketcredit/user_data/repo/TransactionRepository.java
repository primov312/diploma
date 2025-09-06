package com.rocketcredit.user_data.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rocketcredit.user_data.entity.TransactionEntity;

public interface TransactionRepository extends JpaRepository<TransactionEntity,Long> {
  List<TransactionEntity> findByUserId(Long userId);
}