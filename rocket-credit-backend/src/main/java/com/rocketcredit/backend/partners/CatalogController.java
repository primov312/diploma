// AI-assisted (Claude), 2026: inactive partners and products are hidden; reviewed by the author.
package com.rocketcredit.backend.partners;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One marketplace over every partner catalog. Each item still names its seller because
 * financing caps and purchase history are kept per partner; prices stay server-side.
 */
@RestController
public class CatalogController {
    private final PartnerRepository partners;
    private final ProductRepository products;

    public CatalogController(PartnerRepository partners, ProductRepository products) {
        this.partners = partners;
        this.products = products;
    }

    @GetMapping("/api/catalog")
    public List<PartnerDtos.CatalogItem> catalog() {
        Map<Long, PartnerEntity> byId = partners.findAll().stream().filter(PartnerEntity::isActive)
                .collect(Collectors.toMap(PartnerEntity::getId, Function.identity()));
        return products.findAll().stream()
                .filter(p -> p.isActive() && byId.containsKey(p.getPartnerId()))
                .sorted(Comparator.comparing(ProductEntity::getCategory).thenComparing(ProductEntity::getName))
                .map(p -> PartnerDtos.CatalogItem.from(p, byId.get(p.getPartnerId())))
                .toList();
    }
}
