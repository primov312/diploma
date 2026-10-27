// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.consents;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The consent texts shown to the customer, versioned. A record stores the version and a SHA-256 hash of the
 * exact text, so it stays provable what was agreed to even after the wording changes. Changing any text
 * requires a new {@link #VERSION}; customers must then agree again.
 */
public final class ConsentPolicy {
    public static final String VERSION = "consent-v1";

    public record Text(ConsentPurpose purpose, String title, boolean requiredForCredit, String text) {
        public String sha256() { return ConsentPolicy.sha256(text); }
    }

    private static final List<Text> TEXTS = List.of(
            new Text(ConsentPurpose.DATA_PROCESSING, "Processing my data for credit decisions", true,
                    "I agree that Rocket Credit processes my profile details, purchase history, income and expense "
                            + "figures and the credit requests I submit, only to decide my requests and to manage the "
                            + "repayment plans that follow. The data stays in this application and is not sold or shared. "
                            + "I can withdraw this consent at any time; I then cannot submit new credit requests. "
                            + "Decisions and repayment plans that already exist are kept as a record."),
            new Text(ConsentPurpose.AI_ANALYSIS, "AI-assisted analysis", false,
                    "I agree that derived figures about my request (no name or email) are sent to the analysis service "
                            + "so a trained model can add a risk estimate, and that documents or posts I choose to analyse "
                            + "are sent to the AI provider only when I start that analysis. I can withdraw this consent at "
                            + "any time; the rules-based decision stays available."),
            new Text(ConsentPurpose.DIGITAL_FOOTPRINT, "Digital footprint signals", false,
                    "I agree that Rocket Credit may collect and analyse digital footprint signals that I choose to "
                            + "provide: the public posts of my own Facebook account after I connect it, the content of one "
                            + "allowed public page whose address I enter, and the location history shown in this demo. Only "
                            + "pages that allow it in their robots.txt are read. I can withdraw this consent at any time; "
                            + "the stored analysis results and my Facebook connection are then deleted."));

    private static final Map<ConsentPurpose, Text> BY_PURPOSE = TEXTS.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Text::purpose, t -> t));

    private ConsentPolicy() {}

    public static List<Text> texts() { return TEXTS; }

    public static Text text(ConsentPurpose purpose) { return BY_PURPOSE.get(purpose); }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
