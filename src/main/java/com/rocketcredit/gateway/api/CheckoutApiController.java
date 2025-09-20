// src/main/java/com/rocketcredit/gateway/api/CheckoutApiController.java
package com.rocketcredit.gateway.api;

import com.rocketcredit.gateway.idem.IdempotencyService;
import com.rocketcredit.gateway.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckoutApiController implements CheckoutApi {

  private final CheckoutService checkoutService;
  private final IdempotencyService idem;

  @Override
  public ResponseEntity<CheckoutResponse> checkoutPost(CheckoutRequest req) {
    final String partner = req.getPartnerId();
    final String pid     = req.getPartnerPaymentId();

    var replay = idem.replayIfComplete(partner, pid);
    if (replay.isPresent()) return replay.get();

    boolean iAmProducer = idem.startOrSteal(partner, pid);
    if (!iAmProducer) {
      return ResponseEntity.status(409).header("Retry-After", "3").build();
    }

    try {
      CheckoutResponse out = checkoutService.runCheckout(req);

      idem.finish(partner, pid, 200, out, true, null);
      return ResponseEntity.ok(out);

    } catch (Throwable t) {
      CheckoutResponse err = checkoutService.toErrorResponse(req, t);
      idem.finish(partner, pid, 400, err, false, t.getClass().getSimpleName());
      return ResponseEntity.status(400).body(err);
    }
  }
}