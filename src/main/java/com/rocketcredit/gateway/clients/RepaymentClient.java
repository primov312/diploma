package com.rocketcredit.gateway.clients;

import lombok.Data; import lombok.NoArgsConstructor; import lombok.AllArgsConstructor;
import org.springframework.http.*; import org.springframework.stereotype.Component; import org.springframework.web.client.RestTemplate;
import com.rocketcredit.gateway.config.ServiceEndpoints;

import java.util.List;

@Component
public class RepaymentClient extends Client{
    public RepaymentClient(RestTemplate http, ServiceEndpoints ep) { super(http, ep); }

    public PlanResponse createPlan(PlanRequest body) {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<PlanResponse> r = http.exchange(ep.rep + "/repayments", HttpMethod.POST, new HttpEntity<>(body, h), PlanResponse.class);
        if (!r.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("REP -> " + r.getStatusCode());
        return r.getBody();
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PlanRequest {
        private Long userId;
        private String paymentId;
        private Integer installmentDurationMonths;
        private Double amount;
        private String currency;
        private String cardToken;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PlanResponse {
        private String repaymentPlanId;
        private List<Installment> schedule;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Installment {
        private String dueDate;
        private Double amount;
        private String cardToken;
    }
}