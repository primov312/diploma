// src/main/java/com/rocketcredit/gateway/clients/CreditAnalysisClient.java
package com.rocketcredit.gateway.clients;

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
public class CreditAnalysisClient extends Client {

    public CreditAnalysisClient(RestTemplate http, ServiceEndpoints ep) {
        super(http, ep);
    }

    public Decision score(long userId, double cartTotal) {
        var req = Map.of("userId", userId, "cartTotal", cartTotal, "amount", cartTotal);
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Decision> r = http.exchange(ep.cas + "/creditscore", HttpMethod.POST, new HttpEntity<>(req, h), Decision.class);
        if (!r.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("CAS -> " + r.getStatusCode());
        return r.getBody();
    }

    public Decision scoreWithClaim(long userId, double cartTotal, ClaimRef claim) {
        var body = Map.of("userId", userId, "cartTotal", cartTotal, "featuresClaim", claim);
        return post(ep.cas + "/creditscore", body, Decision.class);
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Decision {
        private Boolean approved;
        private String final_reason;
        private Integer score;
    }
}