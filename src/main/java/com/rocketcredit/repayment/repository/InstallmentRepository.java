package com.rocketcredit.repayment.repository;

import com.rocketcredit.repayment.entity.InstallmentEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;

public interface InstallmentRepository extends JpaRepository<InstallmentEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from InstallmentEntity i where i.status='PENDING' and i.dueDate<=:cutoff")
  List<InstallmentEntity> findDueForProcessing(@Param("cutoff") LocalDate cutoff);

  List<InstallmentEntity> findByPlanUidOrderByInstallmentNo(String planUid);
}