// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.consents;

/** What a customer can agree to. Each purpose is a separate, withdrawable choice. */
public enum ConsentPurpose {
    /** Needed to submit any credit request: profile, purchase history and financial figures are processed. */
    DATA_PROCESSING,
    /** Needed when the customer asks for the AI risk estimate or runs an AI-assisted analysis. */
    AI_ANALYSIS,
    /** Needed to collect or analyse digital footprint signals (connected account, public page, location history). */
    DIGITAL_FOOTPRINT
}
