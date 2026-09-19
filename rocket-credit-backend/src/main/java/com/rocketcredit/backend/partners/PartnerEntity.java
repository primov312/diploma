package com.rocketcredit.backend.partners;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "partners")
public class PartnerEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /** Demo financing cap for this partner, USD. */
    @Column(name = "amount_cap", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountCap;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    protected PartnerEntity() {}

    public PartnerEntity(String slug, String displayName, BigDecimal amountCap) {
        this.slug = slug;
        this.displayName = displayName;
        this.amountCap = amountCap;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public BigDecimal getAmountCap() { return amountCap; }
    public String getCurrency() { return currency; }
}
