package com.rocketcredit.gateway.clients;

import lombok.Data; 
import lombok.NoArgsConstructor; 
import lombok.AllArgsConstructor;
import org.springframework.http.*; 
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.rocketcredit.gateway.config.ServiceEndpoints;

@Component
public class NotificationClient extends Client{
    public NotificationClient(RestTemplate http, ServiceEndpoints ep) { super(http, ep); }

    public void send(NotifyRequest body) {
        try {
            HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
            http.exchange(ep.notif + "/notifications", HttpMethod.POST, new HttpEntity<>(body, h), Void.class);
        } catch (Exception ignored) { /* non-blocking */ }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class NotifyRequest {
        private Long userId;
        private String paymentId;
        private String repaymentPlanId;
        private String email;
    }
}