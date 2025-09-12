package com.rocketcredit.user_data.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "social_auths")
@Data
public class SocialAuthEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;  // Matches INTEGER FK to users.id

    @Column(name = "platform", nullable = false)
    private String platform;  // 'instagram', 'facebook', 'vkontakte'

    @Column(name = "token_hash")
    private String tokenHash;
    
    @Column(name = "fetched_data", columnDefinition = "jsonb")
    private String fetchedData;  // JSONB as String (or use JsonNode if Jackson)

    @CreationTimestamp
    @Column(name = "consented_at")
    private ZonedDateTime consentedAt;
}