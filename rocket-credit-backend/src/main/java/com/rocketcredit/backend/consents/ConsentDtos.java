// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
package com.rocketcredit.backend.consents;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public final class ConsentDtos {
    private ConsentDtos() {}

    /** One purpose with the text the customer must read and the state recorded for them. */
    public record Item(ConsentPurpose purpose, String title, String text, boolean requiredForCredit,
                       boolean granted, boolean current, String source, OffsetDateTime decidedAt) {}

    public record View(String policyVersion, List<Item> consents) {}

    public record UpdateRequest(@NotNull Boolean granted) {}
}
