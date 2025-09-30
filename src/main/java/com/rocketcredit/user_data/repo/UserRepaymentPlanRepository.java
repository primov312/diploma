package com.rocketcredit.user_data.repo;

import com.rocketcredit.user_data.entity.UserRepaymentPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepaymentPlanRepository extends JpaRepository<UserRepaymentPlanEntity, Long> {
    Optional<UserRepaymentPlanEntity> findByPlanUid(String planUid);
    int countByUserIdAndStatus(Long userId, String status);
}

