// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.seller;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Everything a seller can read. None of these records has a field for a customer's e-mail, name, financial
 * inputs, address, scores, reason codes or consents; a customer appears only as a pseudonymous reference.
 */
public final class SellerDtos {
    private SellerDtos() {}

    public record Partner(String slug, String name, BigDecimal amountCap, boolean active) {}

    public record DayPoint(LocalDate day, long orders, BigDecimal principal) {}

    public record Overview(Partner partner, Map<String, Long> applicationsByDecision, long financedOrders,
                           BigDecimal financedPrincipal, long activeLoans, long closedLoans, List<DayPoint> last30Days) {}

    /** One financed order: the loan Rocket Credit opened when the customer accepted. */
    public record Order(long orderRef, String customerRef, String productName, BigDecimal principal,
                        String termUnit, int installmentCount, String loanStatus, OffsetDateTime createdAt) {}

    public record Page<T>(List<T> items, int page, int size, long total) {}

    public record Product(long id, String name, String description, String category, String imagePath,
                          String imageAlt, BigDecimal price, boolean active) {}

    public record Image(String path, String alt) {}

    /** The catalogue plus the choices the form may offer, so the client validates with the server's rules. */
    public record Catalogue(Partner partner, List<String> categories, List<Image> images, List<Product> items) {}

    public record ProductForm(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Size(max = 50) String category,
            @NotBlank @Size(max = 180) String imagePath,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price
    ) {}
}
