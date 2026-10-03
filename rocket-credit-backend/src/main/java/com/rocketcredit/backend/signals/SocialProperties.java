package com.rocketcredit.backend.signals;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Meta app settings for the optional "connect your Facebook account" analysis. */
@ConfigurationProperties(prefix = "rocket.social")
public record SocialProperties(
        String metaAppId,
        String metaAppSecret,
        String publicBaseUrl,
        String graphVersion,
        String graphBaseUrl,
        String tokenKey
) {
    public SocialProperties {
        if (metaAppId == null) metaAppId = "";
        if (metaAppSecret == null) metaAppSecret = "";
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) publicBaseUrl = "http://localhost:5173";
        if (graphVersion == null || graphVersion.isBlank()) graphVersion = "v23.0";
        if (graphBaseUrl == null || graphBaseUrl.isBlank()) graphBaseUrl = "https://graph.facebook.com";
        if (tokenKey == null) tokenKey = "";
    }

    public boolean configured() {
        return !metaAppId.isBlank() && !metaAppSecret.isBlank();
    }

    public String redirectUri() {
        return publicBaseUrl.replaceAll("/+$", "") + "/api/me/social-connection/facebook/callback";
    }
}
