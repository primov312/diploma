package com.rocketcredit.creditanalysis.api;

import com.rocketcredit.creditanalysis.model.CreditRequest;
import com.rocketcredit.creditanalysis.model.CreditResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CreditApiController implements CreditscoreApi {

  @Override
  public ResponseEntity<CreditResponse> analyzeCredit(CreditRequest body) {
    // simple placeholder logic:
    int score = (int)(Math.random()*300) + 300;
    boolean approved = score > 400;
    CreditResponse resp = new CreditResponse();
    resp.setScore(score);
    resp.setApproved(approved);
    resp.setReason(approved ? "OK" : "Score too low");
    return ResponseEntity.ok(resp);
  }
}
