package com.rocketcredit.backend.partners;

import com.rocketcredit.backend.common.ApiException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public catalog for the three store pages and the request form. Read-only. */
@RestController
@RequestMapping("/api/partners")
public class PartnerController {
    private final PartnerRepository partners;
    private final ProductRepository products;

    public PartnerController(PartnerRepository partners, ProductRepository products) {
        this.partners = partners;
        this.products = products;
    }

    @GetMapping
    public List<PartnerDtos.PartnerSummary> list() {
        return partners.findAll().stream().map(PartnerDtos.PartnerSummary::from).toList();
    }

    @GetMapping("/{slug}")
    public PartnerDtos.PartnerDetail get(@PathVariable String slug) {
        var p = partners.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Partner"));
        var items = products.findByPartnerIdOrderByName(p.getId()).stream().map(PartnerDtos.Product::from).toList();
        return new PartnerDtos.PartnerDetail(p.getId(), p.getSlug(), p.getDisplayName(), p.getAmountCap(), p.getCurrency(), items);
    }
}
