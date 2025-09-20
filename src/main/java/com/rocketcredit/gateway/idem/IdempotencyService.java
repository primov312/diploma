package com.rocketcredit.gateway.idem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.gateway.api.CheckoutResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

  private final StringRedisTemplate redis;
  private final ObjectMapper om;

  // Tunables
  private static final Duration INPROGRESS_TTL = Duration.ofSeconds(60);
  private static final Duration FINISHED_TTL   = Duration.ofHours(24);
  private static final Duration FRESH_WINDOW   = Duration.ofSeconds(30);

  private String key(String partner, String pid)    { return "idem:%s:%s".formatted(partner, pid); }
  private String lockKey(String partner, String pid){ return "idem:%s:%s:lock".formatted(partner, pid); }

  /** If we already finished for this key, return the previous response immediately. */
  public Optional<ResponseEntity<CheckoutResponse>> replayIfComplete(String partner, String pid) {
    String k = key(partner, pid);
    var ops = redis.opsForHash();
    String status = (String) ops.get(k, "status");
    if (status == null) return Optional.empty();

    if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
      try {
        int http = Integer.parseInt((String) ops.get(k, "httpStatus"));
        String body = (String) ops.get(k, "responseBody");
        return Optional.of(ResponseEntity.status(http).body(om.readValue(body, CheckoutResponse.class)));
      } catch (Exception e) {
        // If corrupt, pretend not found; producer will regenerate
        return Optional.empty();
      }
    }

    if ("IN_PROGRESS".equals(status)) {
      long updated = parseLong((String) ops.get(k, "updatedAt"));
      if (Instant.ofEpochSecond(updated).isAfter(Instant.now().minus(FRESH_WINDOW))) {
        return Optional.of(ResponseEntity.status(409).header("Retry-After", "3").build());
      }
    }
    return Optional.empty();
  }

  /** Try to become the producer by acquiring a short lock (SET NX EX). */
  public boolean startOrSteal(String partner, String pid) {
    String k = key(partner, pid);
    String lk = lockKey(partner, pid);
    long now = Instant.now().getEpochSecond();

    // SET NX EX
    Boolean locked = redis.opsForValue().setIfAbsent(lk, String.valueOf(now), INPROGRESS_TTL);
    if (Boolean.TRUE.equals(locked)) {
      // Initialize/refresh the hash to IN_PROGRESS
      var ops = redis.opsForHash();
      if (!redis.hasKey(k)) {
        ops.putAll(k, Map.of(
            "status", "IN_PROGRESS",
            "httpStatus", "202",
            "responseBody", "{\"status\":\"ACCEPTED\"}",
            "updatedAt", String.valueOf(now)
        ));
      } else {
        ops.put(k, "status", "IN_PROGRESS");
        ops.put(k, "updatedAt", String.valueOf(now));
      }
      redis.expire(k, INPROGRESS_TTL);
      return true;
    }

    // Someone else holds the lock. If stale, attempt takeover.
    var ops = redis.opsForHash();
    String status = (String) ops.get(k, "status");
    if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) return false;

    long updated = parseLong((String) ops.get(k, "updatedAt"));
    if (Instant.ofEpochSecond(updated).isBefore(Instant.now().minus(INPROGRESS_TTL))) {
      // Stale: opportunistic takeover with GETSET pattern
      String old = redis.opsForValue().getAndSet(lk, String.valueOf(now));
      if (old != null) {
        long oldTs = parseLong(old);
        if (Instant.ofEpochSecond(oldTs).isBefore(Instant.now().minus(INPROGRESS_TTL))) {
          redis.expire(lk, INPROGRESS_TTL);
          ops.put(k, "status", "IN_PROGRESS");
          ops.put(k, "updatedAt", String.valueOf(now));
          redis.expire(k, INPROGRESS_TTL);
          return true;
        } else {
          // Not stale; restore lock TTL
          redis.opsForValue().set(lk, old, INPROGRESS_TTL);
        }
      }
    }
    return false;
  }

  /** Save final result (success/failure), extend TTL, release lock. */
  public void finish(String partner, String pid, int httpStatus, CheckoutResponse response, boolean success, String errorCode) {
    String k = key(partner, pid);
    String lk = lockKey(partner, pid);
    try {
      String body = om.writeValueAsString(response);
      var ops = redis.opsForHash();
      ops.put(k, "httpStatus", String.valueOf(httpStatus));
      ops.put(k, "responseBody", body);
      ops.put(k, "status", success ? "SUCCEEDED" : "FAILED");
      ops.put(k, "errorCode", errorCode == null ? "" : errorCode);
      ops.put(k, "updatedAt", String.valueOf(Instant.now().getEpochSecond()));
      redis.expire(k, FINISHED_TTL);
    } catch (Exception ignore) {
      // best effort
    } finally {
      redis.delete(lk); // release lock
    }
  }

  private long parseLong(String s) {
    try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
  }
}