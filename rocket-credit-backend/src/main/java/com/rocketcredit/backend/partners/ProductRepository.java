package com.rocketcredit.backend.partners;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByPartnerIdOrderByName(Long partnerId);
    Optional<ProductEntity> findByFixtureId(String fixtureId);
}
