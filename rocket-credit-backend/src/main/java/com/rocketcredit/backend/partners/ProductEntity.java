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

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "image_path", nullable = false, length = 180)
    private String imagePath;

    @Column(name = "image_alt", nullable = false, length = 250)
    private String imageAlt;

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
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getImagePath() { return imagePath; }
    public String getImageAlt() { return imageAlt; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
}
