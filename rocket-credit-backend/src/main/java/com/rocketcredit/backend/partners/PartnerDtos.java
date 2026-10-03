package com.rocketcredit.backend.partners;

import java.math.BigDecimal;
import java.util.List;

public final class PartnerDtos {
    private PartnerDtos() {}

    public record PartnerSummary(Long id, String slug, String displayName, BigDecimal amountCap, String currency) {
        static PartnerSummary from(PartnerEntity p) {
            return new PartnerSummary(p.getId(), p.getSlug(), p.getDisplayName(), p.getAmountCap(), p.getCurrency());
        }
    }

    public record Product(Long id, String fixtureId, String name, String description, String category,
                          String imagePath, String imageAlt, BigDecimal price, String currency) {
        static Product from(ProductEntity p) {
            return new Product(p.getId(), p.getFixtureId(), p.getName(), p.getDescription(), p.getCategory(),
                    p.getImagePath(), p.getImageAlt(), p.getPrice(), p.getCurrency());
        }
    }

    public record PartnerDetail(Long id, String slug, String displayName, BigDecimal amountCap, String currency,
                                List<Product> products) {}
}
