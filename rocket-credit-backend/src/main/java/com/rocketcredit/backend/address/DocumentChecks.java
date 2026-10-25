package com.rocketcredit.backend.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.rocketcredit.backend.analysis.AnalysisUnavailableException;
import java.util.Collection;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The policy for uploaded address and housing documents. The analysis service reports, for every saved detail,
 * the probability that the document shows it; the decision is made here: a detail is confirmed from
 * {@value #CONFIRM} and contradicted up to {@value #CONTRADICT}. A detail missing from the report counts as unknown.
 */
record DocumentChecks(boolean allShown, boolean anyContradicted) {
    static final double CONFIRM = 0.85;
    static final double CONTRADICT = 0.15;
    private static final Logger log = LoggerFactory.getLogger(DocumentChecks.class);

    static DocumentChecks of(JsonNode extraction, Collection<String> required) {
        JsonNode checks = extraction.path("fieldChecks");
        boolean shown = true, contradicted = false;
        for (String name : required) {
            double probability = checks.path(name).asDouble(0.5);
            shown &= probability >= CONFIRM;
            contradicted |= probability <= CONTRADICT;
        }
        return new DocumentChecks(shown, contradicted);
    }

    static boolean recognized(JsonNode extraction, String documentType) {
        return extraction.path("documentType").asText("").equals(documentType) && extraction.path("confidence").asDouble(0) >= CONFIRM;
    }

    /** Why an upload was not checked: the analysis service was unreachable, its AI is off, or the AI call failed. */
    static String uncheckedReason(JsonNode extraction) {
        return extraction != null && "FIXTURE".equals(extraction.path("analysisMode").asText())
                ? "Automatic document checks are off on this server, so this document needs a manual review"
                : "The document could not be checked automatically right now, so it needs a manual review";
    }

    /** Calls the analysis service; an unreachable service leaves the upload unchecked instead of failing the request. */
    static JsonNode extract(Supplier<JsonNode> call) {
        try {
            return call.get();
        } catch (AnalysisUnavailableException e) {
            log.warn("document check unavailable: {}", e.getMessage());
            return null;
        }
    }

    static boolean extracted(JsonNode extraction) {
        return extraction != null && "EXTRACTED".equals(extraction.path("status").asText());
    }
}
