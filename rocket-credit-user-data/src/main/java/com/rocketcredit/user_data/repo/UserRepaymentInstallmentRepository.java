package com.rocketcredit.user_data.repo;

import com.rocketcredit.user_data.entity.UserRepaymentInstallmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepaymentInstallmentRepository extends JpaRepository<UserRepaymentInstallmentEntity, Long> {
    Optional<UserRepaymentInstallmentEntity> findByPlanUidAndInstallmentNo(String planUid, Integer installmentNo);
    List<UserRepaymentInstallmentEntity> findByPlanUidOrderByInstallmentNo(String planUid);
}

