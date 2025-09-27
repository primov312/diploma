package com.rocketcredit.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Simple edge rate limiter using Redis counters.
 *
 * - Limits per partner (based on mTLS CN or X-Partner-Id)
 * - Limits per client IP (X-Forwarded-For or remote addr)
 *
 * Uses a fixed window counter per key in Redis with TTL = window.
 * A small Lua script ensures INCR+EXPIRE is atomic on first touch.
 */
@Component
@Order(15) // After mTLS (10), before app handlers
public class RateLimitFilter extends OncePerRequestFilter {

  private final StringRedisTemplate redis;

  // Defaults can be overridden via env/properties
  @Value("${RATE_LIMIT_WINDOW_SECONDS:60}")
  private int windowSeconds;

  @Value("${RATE_LIMIT_PER_PARTNER:300}")
  private int perPartner;

  @Value("${RATE_LIMIT_PER_IP:60}")
  private int perIp;

  private static final DefaultRedisScript<Long> INCR_EXPIRE_SCRIPT;

  static {
    // language=lua
    String lua = "local c = redis.call('INCR', KEYS[1]);\n" +
                 "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end\n" +
                 "return c";
    INCR_EXPIRE_SCRIPT = new DefaultRedisScript<>();
    INCR_EXPIRE_SCRIPT.setScriptText(lua);
    INCR_EXPIRE_SCRIPT.setResultType(Long.class);
  }

  public RateLimitFilter(StringRedisTemplate redis) { this.redis = redis; }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Protect only POST /checkout (edge path)
    return !("/checkout".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod()));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Compute identifiers
    String ip = clientIp(request);
    String partner = partnerId(request);

    // First, per-IP limit
    if (perIp > 0 && ip != null) {
      String key = rlKey("ip", ip);
      long count = increment(key, Duration.ofSeconds(windowSeconds));
      if (count > perIp) { tooMany(response, key); return; }
    }

    // Then, per-partner limit (if we can identify)
    if (perPartner > 0 && partner != null) {
      String key = rlKey("partner", partner);
      long count = increment(key, Duration.ofSeconds(windowSeconds));
      if (count > perPartner) { tooMany(response, key); return; }
    }

    filterChain.doFilter(request, response);
  }

  private long increment(String key, Duration ttl) {
    try {
      Long out = redis.execute(INCR_EXPIRE_SCRIPT, Collections.singletonList(key), String.valueOf(ttl.getSeconds()));
      return out == null ? Long.MAX_VALUE : out;
    } catch (Exception e) {
      // Fail-open: if Redis is unavailable, do not block traffic
      return 1L;
    }
  }

  private String rlKey(String scope, String id) {
    return "rl:" + scope + ":" + id + ":" + windowSeconds;
  }

  private void tooMany(HttpServletResponse response, String key) throws IOException {
    long ttl = -1;
    try { ttl = redis.getExpire(key, TimeUnit.SECONDS); } catch (Exception ignore) {}
    if (ttl > 0) response.setHeader("Retry-After", String.valueOf(ttl));
    response.setStatus(429);
    response.setContentType(MediaType.TEXT_PLAIN_VALUE);
    response.getWriter().write("RATE_LIMITED");
  }

  @Nullable
  private String clientIp(HttpServletRequest req) {
    String xff = header(req, "X-Forwarded-For");
    if (xff != null) {
      int comma = xff.indexOf(',');
      return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
    }
    String xri = header(req, "X-Real-IP");
    if (xri != null) return xri;
    try { return req.getRemoteAddr(); } catch (Exception e) { return null; }
  }

  @Nullable
  private String partnerId(HttpServletRequest req) {
    Object cn = req.getAttribute("partner.cn");
    if (cn != null) return String.valueOf(cn);
    String header = header(req, "X-Partner-Id");
    return header;
  }

  private String header(HttpServletRequest req, String name) {
    String v = req.getHeader(name);
    return (v == null || v.isBlank()) ? null : v.trim();
  }
}

