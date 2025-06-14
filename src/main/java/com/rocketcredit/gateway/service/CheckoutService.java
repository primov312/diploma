package com.rocketcredit.gateway.service;

import com.rocketcredit.gateway.model.CheckoutRequest;
import com.rocketcredit.gateway.model.CheckoutResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class CheckoutService {

    private final RestTemplate rest;
    @Value("${services.user-data.url}")        private String userDataUrl;
    @Value("${services.credit-analysis.url}")  private String creditUrl;
    @Value("${services.payment.url}")          private String paymentUrl;
    @Value("${services.repayment.url}")        private String repaymentUrl;
    @Value("${services.notification.url}")     private String notifyUrl;

    public CheckoutService(RestTemplate rest) {
        this.rest = rest;
    }

    public CheckoutResponse runCheckout(CheckoutRequest req) {
        // 1) Fetch user info
        var user = rest.getForObject(userDataUrl + "/users/" + req.getUserId(), Object.class);

        // 2) Credit analysis
        var score = rest.postForObject(creditUrl + "/creditscore", req, Object.class);
        // TODO: inspect score, throw if denied

        // 3) Create payment intent
        var payment = rest.postForObject(paymentUrl + "/payments", req, Object.class);

        // 4) Generate repayment schedule
        var schedule = rest.postForObject(repaymentUrl + "/repayments", req, Object.class);

        // 5) Send notification
        rest.postForLocation(notifyUrl + "/notifications", req);

        // 6) Aggregate into CheckoutResponse
        var resp = new CheckoutResponse();
        resp.setPaymentId("...extract from payment...");
        //resp.setSchedule(/* map schedule to List<Installment> */);
        return resp;
    }

}


