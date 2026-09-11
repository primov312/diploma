package com.rocketcredit.user_data.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Verifies HMAC signatures on internal webhooks originating from trusted services.
 *
 * Required headers:
 *  - X-Timestamp: epoch seconds (±300s allowed skew)
 *  - X-Nonce: idempotency key (planUid)
 *  - X-Signature: base64(HMAC_SHA256(secret, method + "\n" + path + "\n" + ts + "\n" + nonce + "\n" + SHA256(body)))
 */
@Component
@org.springframework.core.annotation.Order(10)
public class WebhookHmacFilter extends OncePerRequestFilter {

  @Value("${WEBHOOK_SECRET:}")
  private String secret;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    boolean endpoint = "/internal/repayment/plan-created".equals(path) && "POST".equalsIgnoreCase(request.getMethod());
    return !endpoint;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (secret == null || secret.isBlank()) { unauthorized(response, "SECRET_NOT_CONFIGURED"); return; }

    String ts = header(request, "X-Timestamp");
    String nonce = header(request, "X-Nonce");
    String sig = header(request, "X-Signature");
    if (ts == null || nonce == null || sig == null) { unauthorized(response, "MISSING_HEADERS"); return; }

    long epoch; try { epoch = Long.parseLong(ts); } catch (Exception e) { unauthorized(response, "BAD_TIMESTAMP"); return; }
    long now = Instant.now().getEpochSecond();
    if (Math.abs(now - epoch) > 300) { unauthorized(response, "TIMESTAMP_SKEW"); return; }

    byte[] body = request.getInputStream().readAllBytes();
    String bodyHash = sha256Hex(body);
    String canonical = request.getMethod() + "\n" + request.getRequestURI() + "\n" + ts + "\n" + nonce + "\n" + bodyHash;
    String expected = hmacB64(secret, canonical);
    if (!constantTimeEquals(expected, sig)) { unauthorized(response, "BAD_SIGNATURE"); return; }

    // Wrap for downstream
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
  private void unauthorized(HttpServletResponse res, String reason) throws IOException { res.setStatus(401); res.setContentType(MediaType.TEXT_PLAIN_VALUE); res.getWriter().write(reason); }

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
      return java.util.Base64.getEncoder().encodeToString(out);
    } catch (Exception e) { throw new RuntimeException(e); }
  }

  private boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    if (a.length() != b.length()) return false;
    int r = 0; for (int i=0;i<a.length();i++) r |= a.charAt(i) ^ b.charAt(i); return r == 0;
  }
}
