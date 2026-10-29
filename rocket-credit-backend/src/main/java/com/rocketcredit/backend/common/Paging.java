// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.common;


/** Shared limits for the seller and administrator lists. */
public final class Paging {
    private Paging() {}

    public static void check(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ApiException.badRequest("INVALID_PAGE", "Use page >= 0 and size 1 to 100.");
    }
}
