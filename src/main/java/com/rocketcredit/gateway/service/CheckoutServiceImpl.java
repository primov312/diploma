package com.rocketcredit.gateway.service;

import com.rocketcredit.gateway.api.CheckoutRequest;
import com.rocketcredit.gateway.api.CheckoutResponse;
import com.rocketcredit.gateway.model.Installment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CheckoutServiceImpl implements CheckoutService {

    private final RestTemplate restTemplate;
    private final String userDataUrl;
    private final String creditAnalysisUrl;
    private final String paymentUrl;
    private final String repaymentUrl;
    private final String notificationUrl;

    public CheckoutServiceImpl(
        RestTemplate restTemplate,
        @Value("${USER_DATA_URL}") String userDataUrl,
        @Value("${CREDIT_ANALYSIS_URL}") String creditAnalysisUrl,
        @Value("${PAYMENT_URL}") String paymentUrl,
        @Value("${REPAYMENT_URL}") String repaymentUrl,
        @Value("${NOTIFICATION_URL}") String notificationUrl
    ) {
        this.restTemplate = restTemplate;
        this.userDataUrl = userDataUrl;
        this.creditAnalysisUrl = creditAnalysisUrl;
        this.paymentUrl = paymentUrl;
        this.repaymentUrl = repaymentUrl;
        this.notificationUrl = notificationUrl;
    }

    @Override
    public CheckoutResponse runCheckout(CheckoutRequest req) {
        // 1) Fetch user info
        restTemplate.getForObject(
          userDataUrl + "/users/" + req.getUserId(),
          Map.class
        );

        // 2) Credit analysis (TODO)
        // Map<?,?> creditResp = restTemplate.postForObject(
        //   creditAnalysisUrl + "/creditscore", req, Map.class);
        // boolean approved = (Boolean) creditResp.get("approved");
        // if (!approved) throw new IllegalStateException("Credit denied");

        // 3) Payment intent (TODO)
        // Map<?,?> paymentResp = restTemplate.postForObject(
        //   paymentUrl + "/payments",
        //   Map.of("userId", req.getUserId(), "amount", req.getCartTotal()),
        //   Map.class);
        // String paymentId = paymentResp.get("paymentId").toString();

        // 4) Repayment schedule (TODO)
        // List<?> rawSchedule = restTemplate.postForObject(
        //   repaymentUrl + "/repayments",
        //   Map.of("paymentId", paymentId, "installments", 3),
        //   List.class);
        // List<Installment> schedule = new ArrayList<>();

        // 5) Notification (TODO)
        // restTemplate.postForLocation(
        //   notificationUrl + "/notifications",
        //   Map.of("userId", req.getUserId(), "paymentId", paymentId)
        // );

        // 6) Placeholder response
        CheckoutResponse resp = new CheckoutResponse();
        resp.setPaymentId("TBD");
        resp.setSchedule(new ArrayList<>());  
        return resp;
    }
}
