package com.rocketcredit.user_data.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.rocketcredit.user_data.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity,Long> {
    Optional<UserEntity> findByPartnerUserId(String partnerUserId);
    Optional<UserEntity> findByEmail(String email);  
}
