// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.consents;

/**
 * Published inside the withdrawing transaction. Modules that hold data collected under the purpose listen for it
 * and erase that data, so the consents package does not depend on them.
 */
public record ConsentWithdrawnEvent(long userId, ConsentPurpose purpose) {}
