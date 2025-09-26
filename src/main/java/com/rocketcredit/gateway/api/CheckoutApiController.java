package com.rocketcredit.gateway.api;

import com.rocketcredit.gateway.idem.IdempotencyService;
import com.rocketcredit.gateway.service.CheckoutService;
import com.rocketcredit.gateway.web.ErrorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckoutApiController implements CheckoutApi {

  private final CheckoutService checkoutService;
  private final IdempotencyService idem;
  private final ErrorMapper errors;

  @Override
  public ResponseEntity<CheckoutResponse> checkoutPost(CheckoutRequest req) {
    final String partner = req.getPartnerId();
    final String pid     = req.getPartnerPaymentId();

    var replay = idem.replayIfComplete(partner, pid);
    if (replay.isPresent()) {
      return ResponseEntity.status(replay.get().getStatusCode())
      .header("Idempotent-Replay", "true")
      .body(replay.get().getBody());
    }

    boolean iAmProducer = idem.startOrSteal(partner, pid);
    if (!iAmProducer) {
      return ResponseEntity.status(409).header("Retry-After", "3").build();
    }

    try {
      CheckoutResponse out = checkoutService.runCheckout(req);

      idem.finish(partner, pid, 200, out, true, null);
      return ResponseEntity.ok(out);

    } catch (Throwable t) {
      var mapping = errors.map(t);
      CheckoutResponse err = errors.toCheckoutError(req, mapping);
      idem.finish(partner, pid, mapping.status(), err, false, errorsCodeFor(t));
      return ResponseEntity.status(mapping.status()).body(err);
    }
  }

  private String errorsCodeFor(Throwable t) {
    try { return t.getClass().getSimpleName(); } catch (Throwable ignore) { return "ERROR"; }
  }
}
