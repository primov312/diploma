// package com.rocketcredit.gateway.security;

// import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// import org.springframework.data.redis.core.StringRedisTemplate;
// import org.springframework.http.MediaType;
// import org.springframework.stereotype.Component;
// import org.springframework.web.filter.OncePerRequestFilter;

// import javax.servlet.FilterChain;
// import javax.servlet.ServletException;
// import javax.servlet.http.HttpServletRequest;
// import javax.servlet.http.HttpServletResponse;
// import java.io.IOException;
// import java.security.cert.X509Certificate;
// import java.time.Instant;

// @Component
// @ConditionalOnProperty(name = "MTLS_ENABLED", havingValue = "true")
// @org.springframework.core.annotation.Order(10)
// public class MtlsAuthFilter extends OncePerRequestFilter {
//   private final StringRedisTemplate redis;

//   public MtlsAuthFilter(StringRedisTemplate redis) { this.redis = redis; }

//   @Override
//   protected boolean shouldNotFilter(HttpServletRequest request) {
//     return !("/checkout".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod()));
//   }

//   @Override
//   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
//       throws ServletException, IOException {

//     Object attr = request.getAttribute("javax.servlet.request.X509Certificate");
//     if (!(attr instanceof X509Certificate[])) { unauthorized(response, "NO_CLIENT_CERT"); return; }
//     X509Certificate[] chain = (X509Certificate[]) attr;
//     if (chain.length == 0) { unauthorized(response, "NO_CLIENT_CERT"); return; }
//     X509Certificate cert = chain[0];
//     String dn = cert.getSubjectX500Principal().getName();
//     String cn = extractCN(dn);
//     // propagate identity for downstream checks (e.g., HMAC)
//     request.setAttribute("partner.cn", cn);
//     request.setAttribute("auth.mtls", Boolean.TRUE);

//     // Optional: cross-check body partnerId header
//     String partnerFromHeader = request.getHeader("X-Partner-Id");
//     if (partnerFromHeader != null && !partnerFromHeader.equals(cn)) { unauthorized(response, "PARTNER_MISMATCH"); return; }

//     // Replay guard
//     String ts = header(request, "X-Timestamp");
//     String nonce = header(request, "X-Nonce");
//     if (ts == null || nonce == null) { unauthorized(response, "MISSING_TS_NONCE"); return; }
//     long epoch; try { epoch = Long.parseLong(ts); } catch (Exception e) { unauthorized(response, "BAD_TIMESTAMP"); return; }
//     long now = Instant.now().getEpochSecond(); if (Math.abs(now-epoch) > 300) { unauthorized(response, "TIMESTAMP_SKEW"); return; }
//     Boolean ok = redis.opsForValue().setIfAbsent("auth:nonce:"+cn+":"+nonce, String.valueOf(now), java.time.Duration.ofMinutes(5));
//     if (!Boolean.TRUE.equals(ok)) { conflict(response, "REPLAY_DETECTED"); return; }

//     // All good; continue
//     filterChain.doFilter(request, response);
//   }

//   private String header(HttpServletRequest req, String name) { String v = req.getHeader(name); return (v==null||v.isBlank())?null:v.trim(); }
//   private void unauthorized(HttpServletResponse res, String reason) throws IOException { res.setStatus(401); res.setContentType(MediaType.TEXT_PLAIN_VALUE); res.getWriter().write(reason); }
//   private void conflict(HttpServletResponse res, String reason) throws IOException { res.setStatus(409); res.setContentType(MediaType.TEXT_PLAIN_VALUE); res.getWriter().write(reason); }

//   private String extractCN(String dn) {
//     if (dn == null) return null;
//     for (String part : dn.split(",")) {
//       String p = part.trim();
//       if (p.startsWith("CN=")) return p.substring(3);
//     }
//     return dn; // fallback
//   }
// }
