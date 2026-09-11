package com.rocketcredit.repayment.service;

import com.rocketcredit.repayment.model.InstallmentItem;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class UserDataNotifier {
  private static final Logger log = LoggerFactory.getLogger(UserDataNotifier.class);

  @Value("${userData.baseUrl:http://user-data:8080}")
  String userDataBaseUrl;

  @Value("${userData.webhookSecret:}")
  String webhookSecret;

  private RestTemplate restTemplate = new RestTemplateBuilder()
      .setConnectTimeout(java.time.Duration.ofSeconds(2))
      .setReadTimeout(java.time.Duration.ofSeconds(3))
      .build();

  public record Installment(int number, String dueDate, java.math.BigDecimal amount, String status) {}
  public record PlanCreatedEvent(String planUid, long userId, java.math.BigDecimal totalAmount, String currency, int installments, List<Installment> schedule) {}

  public void notifyPlanCreated(String planUid, long userId, java.math.BigDecimal totalAmount, String currency, int installments, List<InstallmentItem> schedule) {
    if (webhookSecret == null || webhookSecret.isBlank()) {
      log.warn("user-data webhook secret not configured; skipping notify");
      return;
    }
    try {
      List<Installment> list = schedule.stream()
          .map(i -> new Installment(i.installmentNo(), i.dueDate(), i.amount(), i.status()))
          .toList();
      var evt = new PlanCreatedEvent(planUid, userId, totalAmount, currency, installments, list);
      String url = userDataBaseUrl + "/internal/repayment/plan-created";

      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      byte[] body = mapper.writeValueAsBytes(evt);
      HttpHeaders h = new HttpHeaders();
      h.setContentType(MediaType.APPLICATION_JSON);
      String ts = String.valueOf(Instant.now().getEpochSecond());
      String nonce = planUid; // idempotency key
      String bHash = sha256Hex(body);
      String canonical = "POST\n/internal/repayment/plan-created\n" + ts + "\n" + nonce + "\n" + bHash;
      String sig = hmacB64(webhookSecret, canonical);
      h.set("X-Timestamp", ts);
      h.set("X-Nonce", nonce);
      h.set("X-Signature", sig);

      var req = new HttpEntity<>(body, h);
      restTemplate.exchange(url, HttpMethod.POST, req, String.class);
      log.info("Notified user-data of plan uid={} (userIdHash={})", planUid, Integer.toHexString(Long.toString(userId).hashCode()));
    } catch (Exception e) {
      log.error("Failed to notify user-data of plan uid={} err={}", planUid, e.toString());
    }
  }

  private static String sha256Hex(byte[] data) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] d = md.digest(data);
    StringBuilder sb = new StringBuilder(d.length*2);
    for (byte b: d) sb.append(String.format("%02x", b));
    return sb.toString();
  }
  private static String hmacB64(String secret, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(out);
  }
}
