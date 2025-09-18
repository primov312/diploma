package com.rocketcredit.gateway.clients;

import lombok.Data; 
import lombok.NoArgsConstructor; 
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.*; 
import org.springframework.stereotype.Component; 
import org.springframework.web.client.RestTemplate;

import com.rocketcredit.gateway.config.ServiceEndpoints;
import com.rocketcredit.gateway.model.Buyer;

@Component
public class PaymentClient extends Client{
    public PaymentClient(RestTemplate http, ServiceEndpoints ep) { super(http, ep); }

    public CreatePaymentResponse create(CreatePaymentRequest body) {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<CreatePaymentResponse> r = http.exchange(ep.pay + "/payments/transfers", HttpMethod.POST, new HttpEntity<>(body, h), CreatePaymentResponse.class);
        if (!r.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("PAY -> " + r.getStatusCode());
        return r.getBody();
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreatePaymentRequest {
        private Long userId;
        private String partnerPaymentId;
        private Double amount;
        private String currency;
        private Buyer payee;
        private List<ItemDto> items;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreatePaymentResponse {
        private Long id;
        private String status;
        public String getPaymentId() { return id == null ? null : String.valueOf(id); }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ItemDto {
        private String sku;
        private String name;
        private BigDecimal price;
        private Integer quantity;
    }
}
