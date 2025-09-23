package com.rocketcredit.gateway.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.ServletInputStream;
import javax.servlet.ReadListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
@org.springframework.core.annotation.Order(20)
public class HmacAuthFilter extends OncePerRequestFilter {
  private final PartnerSecretService secrets;
  private final StringRedisTemplate redis;

  public HmacAuthFilter(PartnerSecretService secrets, StringRedisTemplate redis) {
    this.secrets = secrets; this.redis = redis;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !("/checkout".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod()));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Extract headers
    String partner = header(request, "X-Partner-Id");
    String ts = header(request, "X-Timestamp");
    String nonce = header(request, "X-Nonce");
    String sig = header(request, "X-Signature");

    if (partner == null || ts == null || nonce == null || sig == null) {
      unauthorized(response, "MISSING_HEADERS");
      return;
    }

    // If mTLS already authenticated a CN, require it matches header partner
    Object cnAttr = request.getAttribute("partner.cn");
    if (cnAttr != null && !partner.equals(cnAttr.toString())) {
      unauthorized(response, "PARTNER_MISMATCH");
      return;
    }

    // Parse and validate timestamp (5 min skew)
    long epoch;
    try { epoch = Long.parseLong(ts); } catch (Exception e) { unauthorized(response, "BAD_TIMESTAMP"); return; }
    long now = Instant.now().getEpochSecond();
    if (Math.abs(now - epoch) > 300) { unauthorized(response, "TIMESTAMP_SKEW"); return; }

    // Replay protection: nonce must be new
    String nonceKey = "auth:nonce:" + partner + ":" + nonce;
    Boolean ok = redis.opsForValue().setIfAbsent(nonceKey, String.valueOf(now), java.time.Duration.ofMinutes(5));
    if (!Boolean.TRUE.equals(ok)) { conflict(response, "REPLAY_DETECTED"); return; }

    // Read body
    byte[] body = request.getInputStream().readAllBytes();
    // Compute signature
    String secret = secrets.secretFor(partner).orElse(null);
    if (secret == null) { unauthorized(response, "UNKNOWN_PARTNER"); return; }
    String bodyHash = sha256Hex(body);
    String canonical = request.getMethod() + "\n" + request.getRequestURI() + "\n" + ts + "\n" + nonce + "\n" + bodyHash;
    String expected = hmacB64(secret, canonical);
    if (!constantTimeEquals(expected, sig)) { unauthorized(response, "BAD_SIGNATURE"); return; }

    // Mark that HMAC auth succeeded for downstream
    request.setAttribute("auth.hmac", Boolean.TRUE);

    // Wrap request so downstream can read the body again
    var wrapped = new HttpServletRequestWrapper(request) {
      @Override public ServletInputStream getInputStream() {
        return new ServletInputStream() {
          private int idx = 0;
          @Override public int read() { return idx < body.length ? (body[idx++] & 0xFF) : -1; }
          @Override public boolean isFinished() { return idx >= body.length; }
          @Override public boolean isReady() { return true; }
          @Override public void setReadListener(ReadListener readListener) {}
        }; }
      @Override public java.io.BufferedReader getReader() { return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
      @Override public String getContentType() { return request.getContentType(); }
      @Override public int getContentLength() { return body.length; }
      @Override public long getContentLengthLong() { return body.length; }
    };

    filterChain.doFilter(wrapped, response);
  }

  private String header(HttpServletRequest req, String name) { String v = req.getHeader(name); return (v==null||v.isBlank())?null:v.trim(); }

  private void unauthorized(HttpServletResponse res, String reason) throws IOException {
    res.setStatus(401);
    res.setContentType(MediaType.TEXT_PLAIN_VALUE);
    res.getWriter().write(reason);
  }

  private void conflict(HttpServletResponse res, String reason) throws IOException {
    res.setStatus(409);
    res.setContentType(MediaType.TEXT_PLAIN_VALUE);
    res.getWriter().write(reason);
  }

  private String sha256Hex(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(data);
      StringBuilder sb = new StringBuilder(d.length*2);
      for (byte b: d) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) { throw new RuntimeException(e); }
  }

  private String hmacB64(String secret, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception e) { throw new RuntimeException(e); }
  }

  private boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    if (a.length() != b.length()) return false;
    int r = 0; for (int i=0;i<a.length();i++) r |= a.charAt(i) ^ b.charAt(i); return r == 0;
  }
}
