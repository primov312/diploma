package com.rocketcredit.gateway.api;

import com.rocketcredit.gateway.model.CheckoutRequest;
import com.rocketcredit.gateway.model.CheckoutResponse;
import com.rocketcredit.gateway.service.CheckoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutApiController implements CheckoutApi {

    private final CheckoutService checkoutService;

    public CheckoutApiController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @Override
    public ResponseEntity<CheckoutResponse> checkoutPost(CheckoutRequest checkoutRequest) {
        CheckoutResponse resp = checkoutService.runCheckout(checkoutRequest);
        return ResponseEntity.ok(resp);
    }
}
