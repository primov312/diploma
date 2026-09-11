package com.rocketcredit.claimcheck;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimRef {
  private String bucket;        // e.g., "rc-features"
  private String key;           // e.g., "features/4/3f6c-...-uuid.json"
  private String contentType;   // "application/json"
  private long   size;          // bytes
  private String sha256;        // hex integrity
  private String correlationId; // trace across services
  private Long   expiresAt;     // epoch millis (optional)
}