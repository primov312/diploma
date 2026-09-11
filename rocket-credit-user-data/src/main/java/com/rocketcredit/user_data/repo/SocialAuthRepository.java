package com.rocketcredit.user_data.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rocketcredit.user_data.entity.SocialAuthEntity;

@Repository
public interface SocialAuthRepository extends JpaRepository<SocialAuthEntity, Long> {
    SocialAuthEntity findByUserIdAndPlatform(Long userId, String platform);
}