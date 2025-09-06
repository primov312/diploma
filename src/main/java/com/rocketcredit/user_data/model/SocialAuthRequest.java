package com.rocketcredit.user_data.model;

import lombok.Data;

@Data
public class SocialAuthRequest {
    private Long userId;
    private String platform;
    private String authCode;
    private boolean consentGiven;
}