// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.seller;

import com.rocketcredit.backend.analysis.AnalysisProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sellers see a customer only as {@code C-} plus six hex characters of an HMAC-SHA256 over the partner and
 * the user ID (data minimisation, GDPR Art. 5(1)(c) and Art. 4(5)). The key is the existing analysis shared
 * secret, so no new secret is stored in the repository. The partner is part of the message, so two sellers
 * get different references for the same customer and cannot link their order lists. Without a configured
 * secret (local development) a random key is used, and references stay stable only until the next restart.
 */
@Component
public class CustomerPseudonyms {
    private static final Logger log = LoggerFactory.getLogger(CustomerPseudonyms.class);
    private final SecretKeySpec key;

    public CustomerPseudonyms(AnalysisProperties analysis) {
        byte[] secret = analysis.sharedSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length == 0) {
            log.warn("no analysis shared secret configured; customer references change on restart");
            secret = new byte[32];
            new SecureRandom().nextBytes(secret);
        }
        this.key = new SecretKeySpec(secret, "HmacSHA256");
    }

    public String of(long partnerId, long userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(("customer:" + partnerId + ":" + userId).getBytes(StandardCharsets.UTF_8));
            return "C-" + HexFormat.of().formatHex(digest).substring(0, 6).toUpperCase();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
