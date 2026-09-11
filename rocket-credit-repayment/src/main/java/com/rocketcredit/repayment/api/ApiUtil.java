package com.rocketcredit.repayment.api;
import org.springframework.http.ResponseEntity;

public final class ApiUtil {
  private ApiUtil() {}
  public static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok(body); }
}