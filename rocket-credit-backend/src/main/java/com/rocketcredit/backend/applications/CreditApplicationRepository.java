package com.rocketcredit.backend.applications;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Ownership is part of every lookup; there is no findById(Long) without the user. */
public interface CreditApplicationRepository extends JpaRepository<CreditApplicationEntity, Long> {
    List<CreditApplicationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<CreditApplicationEntity> findByIdAndUserId(Long id, Long userId);
}
