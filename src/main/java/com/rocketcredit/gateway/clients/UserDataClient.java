package com.rocketcredit.gateway.clients;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.rocketcredit.claimcheck.ClaimRef;
import com.rocketcredit.gateway.config.ServiceEndpoints;

import java.util.List;
import java.util.Map;
import java.net.ConnectException;
import java.time.LocalDate;

@Component
public class UserDataClient extends Client{

    public UserDataClient(RestTemplate http, ServiceEndpoints ep) {
        super(http, ep);
    }

    @Retryable(
      value = { ResourceAccessException.class, ConnectException.class },
      maxAttempts = 6,
      backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5000))
    public User resolve(ResolveUserRequest body) {
        return post(ep.uds + "/users/resolve", body, User.class);
    }

    public Map<String,Object> getFeatures(long userId) {
        return get(ep.uds + "/user-data?id=" + userId, Map.class);
    }

    @Retryable(
      value = { ResourceAccessException.class, ConnectException.class },
      maxAttempts = 6,
      backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5000))
    public ClaimRef createFeatureClaim(long userId) {
        return post(ep.uds + "/feature-claims", Map.of("userId", userId), ClaimRef.class);
    }

    // ==== DTOs ====
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ResolveUserRequest {
        private String partnerUserId;
        private String email;
        private String name;
        private String payment;
        private List<TransactionDto> transactions;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class User {
        private Long id;
        private String name;
        private String email;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String partnerUserId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDto {
        private String id;
        private LocalDate date;
        private java.math.BigDecimal amount;
        private String method;
    }
}
