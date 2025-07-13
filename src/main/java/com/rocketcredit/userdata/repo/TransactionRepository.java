package com.rocketcredit.userdata.repo;

import com.rocketcredit.userdata.entity.TransactionEntity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity,Long> {
  List<TransactionEntity> findByUserId(Long userId);
}