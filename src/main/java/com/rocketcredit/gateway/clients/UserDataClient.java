package com.rocketcredit.gateway.clients;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.rocketcredit.claimcheck.ClaimRef;
import com.rocketcredit.gateway.config.ServiceEndpoints;

import java.util.Map;

@Component
public class UserDataClient extends Client{

    public UserDataClient(RestTemplate http, ServiceEndpoints ep) {
        super(http, ep);
    }

    public User resolve(ResolveUserRequest body) {
        return post(ep.uds + "/users/resolve", body, User.class);
    }

    public Map<String,Object> getFeatures(long userId) {
        return get(ep.uds + "/user-data?id=" + userId, Map.class);
    }

    public ClaimRef createFeatureClaim(long userId) {
        return post(ep.uds + "/feature-claims", Map.of("userId", userId), ClaimRef.class);
    }

    // ==== DTOs ====
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ResolveUserRequest {
        private String partnerUserId;
        private String email;
        private String name;
        private String cardToken;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class User {
        private Long id;
        private String name;
        private String email;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String partnerUserId;
    }
}