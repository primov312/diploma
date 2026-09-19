package com.rocketcredit.backend.transactions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query takes the owning user ID: callers derive it from the session,
 * never from the request, so one customer cannot read another's history.
 */
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    List<TransactionEntity> findByUserIdOrderByOccurredOnDesc(Long userId);
    List<TransactionEntity> findByUserIdAndPartnerIdOrderByOccurredOnDesc(Long userId, Long partnerId);
    Optional<TransactionEntity> findByIdAndUserId(Long id, Long userId);
    boolean existsByFixtureId(String fixtureId);
}
