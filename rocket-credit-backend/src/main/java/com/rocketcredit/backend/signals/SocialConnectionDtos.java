package com.rocketcredit.backend.signals;

import java.time.Instant;

public final class SocialConnectionDtos {
    private SocialConnectionDtos() {}
    public record Status(boolean configured, boolean connected, String provider, String displayName,
                         Instant connectedAt, Instant expiresAt) {}
    public record StartResponse(String authorizeUrl) {}
}
