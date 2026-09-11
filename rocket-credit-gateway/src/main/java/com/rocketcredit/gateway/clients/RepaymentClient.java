package com.rocketcredit.gateway.clients;

import lombok.Data; import lombok.NoArgsConstructor; import lombok.AllArgsConstructor;
import org.springframework.http.*; import org.springframework.stereotype.Component; import org.springframework.web.client.RestTemplate;
import com.rocketcredit.gateway.config.ServiceEndpoints;
import com.rocketcredit.gateway.model.Buyer;

import java.math.BigDecimal;
import java.util.List;

@Component
public class RepaymentClient extends Client{
    public RepaymentClient(RestTemplate http, ServiceEndpoints ep) { super(http, ep); }

    public PlanResponse createPlan(PlanRequest body) {
        return post(ep.rep + "/repayments", body, PlanResponse.class);
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PlanRequest {
        private Long userId;
        private String paymentId;
        private String partnerPaymentId;
        private Integer installmentDurationMonths;
        private BigDecimal totalAmount;
        private String currency;
        private Buyer buyer;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PlanResponse {
        private String repaymentPlanId;
        private List<Installment> schedule;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Installment {
        private String dueDate;
        private java.math.BigDecimal amount;
        private String cardToken;
    }
}
