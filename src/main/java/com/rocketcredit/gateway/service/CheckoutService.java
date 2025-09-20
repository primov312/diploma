package com.rocketcredit.gateway.service;

import com.rocketcredit.gateway.api.CheckoutRequest;
import com.rocketcredit.gateway.api.CheckoutResponse;

public interface CheckoutService {
    CheckoutResponse runCheckout(CheckoutRequest request);
    CheckoutResponse toErrorResponse(CheckoutRequest request, Throwable t);
}
