package com.rocketcredit.userdata.repo;

import com.rocketcredit.userdata.entity.SocialAuthEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialAuthRepository extends JpaRepository<SocialAuthEntity, Long> {
    SocialAuthEntity findByUserIdAndPlatform(Long userId, String platform);
}