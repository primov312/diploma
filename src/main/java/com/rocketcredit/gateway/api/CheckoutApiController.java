package com.rocketcredit.gateway.api;

import com.rocketcredit.gateway.model.CheckoutRequest;
import com.rocketcredit.gateway.model.CheckoutResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutApiController implements CheckoutApi {

    @Override
    public ResponseEntity<CheckoutResponse> checkoutPost(CheckoutRequest checkoutRequest) {
        CheckoutResponse response = new CheckoutResponse();
        response.setPaymentId("test-payment-id");
        return ResponseEntity.ok(response);
    }
}
