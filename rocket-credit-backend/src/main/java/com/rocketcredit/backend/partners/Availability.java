// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.partners;

import com.rocketcredit.backend.common.ApiException;

/** One place for the 409 answers when an administrator or seller has switched a partner or product off. */
public final class Availability {
    private Availability() {}

    public static PartnerEntity require(PartnerEntity partner) {
        if (!partner.isActive()) throw ApiException.conflict("PARTNER_UNAVAILABLE", "This store is not offering credit at the moment.");
        return partner;
    }

    public static ProductEntity require(ProductEntity product) {
        if (!product.isActive()) throw ApiException.conflict("PRODUCT_UNAVAILABLE", "This product is no longer available.");
        return product;
    }
}
