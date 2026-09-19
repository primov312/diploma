package com.rocketcredit.backend.partners;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class ProductEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "fixture_id", nullable = false, unique = true, length = 80)
    private String fixtureId;

    @Column(nullable = false, length = 160)
    private String name;

    /** Authoritative price; query-string prices from store pages are ignored. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    protected ProductEntity() {}

    public ProductEntity(Long partnerId, String fixtureId, String name, BigDecimal price) {
        this.partnerId = partnerId;
        this.fixtureId = fixtureId;
        this.name = name;
        this.price = price;
    }

    public Long getId() { return id; }
    public Long getPartnerId() { return partnerId; }
    public String getFixtureId() { return fixtureId; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
}
