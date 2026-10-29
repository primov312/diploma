// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.seller;

import com.rocketcredit.backend.audit.AuditLog;
import com.rocketcredit.backend.auth.AuthenticatedUser;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.common.Paging;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The seller's view of its own partner. Every method takes the partner ID from the signed-in principal;
 * no request parameter can name another partner, and a product of another partner is answered with 404
 * so its existence does not leak. Catalogue changes never touch quotes, applications or loans: those keep
 * their own copy of the price, and a pending purchase with an old price fails with TERMS_CHANGED.
 */
@Service
public class SellerService {
    static final Set<String> LOAN_STATUSES = Set.of("ACTIVE", "PAID", "SETTLED_EARLY");
    private static final String BUDAPEST = "'Europe/Budapest'";

    private final JdbcTemplate jdbc;
    private final CustomerPseudonyms pseudonyms;
    private final AuditLog audit;

    public SellerService(JdbcTemplate jdbc, CustomerPseudonyms pseudonyms, AuditLog audit) {
        this.jdbc = jdbc;
        this.pseudonyms = pseudonyms;
        this.audit = audit;
    }

    private static long partnerOf(AuthenticatedUser seller) {
        if (seller.getPartnerId() == null) throw new IllegalStateException("seller without partner " + seller.getId());
        return seller.getPartnerId();
    }

    private SellerDtos.Partner partner(long partnerId) {
        return jdbc.queryForObject("SELECT slug, display_name, amount_cap, active FROM partners WHERE id=?",
                (rs, n) -> new SellerDtos.Partner(rs.getString("slug"), rs.getString("display_name"),
                        rs.getBigDecimal("amount_cap"), rs.getBoolean("active")), partnerId);
    }

    @Transactional(readOnly = true)
    public SellerDtos.Overview overview(AuthenticatedUser seller) {
        long partnerId = partnerOf(seller);
        Map<String, Long> byDecision = new LinkedHashMap<>();
        for (String status : List.of("APPROVED", "REVIEW", "REJECTED")) byDecision.put(status, 0L);
        jdbc.query("SELECT decision_status, count(*) FROM credit_applications WHERE partner_id=? GROUP BY decision_status",
                rs -> { byDecision.put(rs.getString(1), rs.getLong(2)); }, partnerId);
        var loans = jdbc.queryForMap("""
                SELECT count(*) AS orders, COALESCE(sum(principal), 0) AS principal,
                       count(*) FILTER (WHERE status = 'ACTIVE') AS active, count(*) FILTER (WHERE status <> 'ACTIVE') AS closed
                FROM loans WHERE partner_id=?""", partnerId);
        var trend = jdbc.query("""
                SELECT d::date AS day, count(l.id) AS orders, COALESCE(sum(l.principal), 0) AS principal
                FROM generate_series((now() AT TIME ZONE %1$s)::date - 29, (now() AT TIME ZONE %1$s)::date, interval '1 day') d
                LEFT JOIN loans l ON l.partner_id = ? AND (l.created_at AT TIME ZONE %1$s)::date = d::date
                GROUP BY d ORDER BY d""".formatted(BUDAPEST),
                (rs, n) -> new SellerDtos.DayPoint(rs.getObject("day", LocalDate.class), rs.getLong("orders"),
                        rs.getBigDecimal("principal")), partnerId);
        return new SellerDtos.Overview(partner(partnerId), byDecision, ((Number) loans.get("orders")).longValue(),
                ((BigDecimal) loans.get("principal")).setScale(2, RoundingMode.UNNECESSARY),
                ((Number) loans.get("active")).longValue(), ((Number) loans.get("closed")).longValue(), trend);
    }

    @Transactional(readOnly = true)
    public SellerDtos.Page<SellerDtos.Order> orders(AuthenticatedUser seller, String status, LocalDate from, LocalDate to,
                                                    int page, int size) {
        long partnerId = partnerOf(seller);
        Paging.check(page, size);
        StringBuilder where = new StringBuilder(" WHERE l.partner_id = ?");
        List<Object> args = new ArrayList<>(List.of(partnerId));
        if (status != null && !status.isBlank()) {
            if (!LOAN_STATUSES.contains(status)) throw ApiException.badRequest("INVALID_STATUS", "Unknown loan status.");
            where.append(" AND l.status = ?");
            args.add(status);
        }
        if (from != null) { where.append(" AND (l.created_at AT TIME ZONE " + BUDAPEST + ")::date >= ?"); args.add(from); }
        if (to != null) { where.append(" AND (l.created_at AT TIME ZONE " + BUDAPEST + ")::date <= ?"); args.add(to); }
        long total = jdbc.queryForObject("SELECT count(*) FROM loans l" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var items = jdbc.query("""
                SELECT l.id, l.user_id, pr.name AS product_name, l.principal, l.term_unit, l.installment_count, l.status, l.created_at
                FROM loans l LEFT JOIN products pr ON pr.id = l.product_id""" + where + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new SellerDtos.Order(rs.getLong("id"), pseudonyms.of(partnerId, rs.getLong("user_id")),
                        rs.getString("product_name"), rs.getBigDecimal("principal"), rs.getString("term_unit"),
                        rs.getInt("installment_count"), rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class)),
                args.toArray());
        return new SellerDtos.Page<>(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public SellerDtos.Catalogue catalogue(AuthenticatedUser seller) {
        long partnerId = partnerOf(seller);
        var items = jdbc.query("""
                SELECT id, name, description, category, image_path, image_alt, price, active
                FROM products WHERE partner_id = ? ORDER BY active DESC, category, name""",
                (rs, n) -> new SellerDtos.Product(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                        rs.getString("category"), rs.getString("image_path"), rs.getString("image_alt"),
                        rs.getBigDecimal("price"), rs.getBoolean("active")), partnerId);
        return new SellerDtos.Catalogue(partner(partnerId), categories(), images(), items);
    }

    /** Existing categories of the whole catalogue; a seller cannot invent new ones. */
    private List<String> categories() {
        return jdbc.queryForList("SELECT DISTINCT category FROM products WHERE category IS NOT NULL ORDER BY category", String.class);
    }

    /**
     * The existing static image set: every image path already used in the catalogue. Every write is checked
     * against this list, so the set never grows and no upload is possible.
     */
    private List<SellerDtos.Image> images() {
        return jdbc.query("""
                SELECT image_path, min(image_alt) AS image_alt FROM products WHERE image_path IS NOT NULL
                GROUP BY image_path ORDER BY image_path""",
                (rs, n) -> new SellerDtos.Image(rs.getString("image_path"), rs.getString("image_alt")));
    }

    @Transactional
    public SellerDtos.Product create(AuthenticatedUser seller, SellerDtos.ProductForm form) {
        long partnerId = partnerOf(seller);
        var valid = validate(partnerId, form);
        String slug = jdbc.queryForObject("SELECT slug FROM partners WHERE id=?", String.class, partnerId);
        String fixtureId = "seller-" + slug + "-" + UUID.randomUUID().toString().substring(0, 13);
        long id = jdbc.queryForObject("""
                INSERT INTO products(partner_id, fixture_id, name, description, category, image_path, image_alt, price)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                partnerId, fixtureId, valid.name(), valid.description(), valid.category(), valid.imagePath(), valid.imageAlt(), valid.price());
        audit.record(seller, "SELLER_PRODUCT_CREATED", "PRODUCT", id, Map.of("name", valid.name(), "price", valid.price().toPlainString()));
        return product(partnerId, id);
    }

    @Transactional
    public SellerDtos.Product update(AuthenticatedUser seller, long id, SellerDtos.ProductForm form) {
        long partnerId = partnerOf(seller);
        var before = product(partnerId, id);
        var valid = validate(partnerId, form);
        jdbc.update("""
                UPDATE products SET name=?, description=?, category=?, image_path=?, image_alt=?, price=?
                WHERE id=? AND partner_id=?""",
                valid.name(), valid.description(), valid.category(), valid.imagePath(), valid.imageAlt(), valid.price(), id, partnerId);
        var details = new LinkedHashMap<String, Object>();
        details.put("name", valid.name());
        details.put("price", valid.price().toPlainString());
        if (before.price().compareTo(valid.price()) != 0) details.put("previousPrice", before.price().toPlainString());
        audit.record(seller, "SELLER_PRODUCT_UPDATED", "PRODUCT", id, details);
        return product(partnerId, id);
    }

    @Transactional
    public SellerDtos.Product setActive(AuthenticatedUser seller, long id, boolean active) {
        long partnerId = partnerOf(seller);
        product(partnerId, id);
        jdbc.update("UPDATE products SET active=? WHERE id=? AND partner_id=?", active, id, partnerId);
        audit.record(seller, active ? "SELLER_PRODUCT_ACTIVATED" : "SELLER_PRODUCT_DEACTIVATED", "PRODUCT", id, Map.of());
        return product(partnerId, id);
    }

    /** 404 both for a missing product and for another partner's product. */
    private SellerDtos.Product product(long partnerId, long id) {
        return jdbc.query("""
                SELECT id, name, description, category, image_path, image_alt, price, active
                FROM products WHERE id = ? AND partner_id = ? FOR UPDATE""",
                (rs, n) -> new SellerDtos.Product(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                        rs.getString("category"), rs.getString("image_path"), rs.getString("image_alt"),
                        rs.getBigDecimal("price"), rs.getBoolean("active")), id, partnerId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Product"));
    }

    private record Valid(String name, String description, String category, String imagePath, String imageAlt, BigDecimal price) {}

    private Valid validate(long partnerId, SellerDtos.ProductForm form) {
        String name = form.name().trim();
        String description = form.description().trim();
        if (name.isEmpty()) throw ApiException.badRequest("INVALID_NAME", "Enter a product name of 1 to 160 characters.");
        if (description.isEmpty()) throw ApiException.badRequest("INVALID_DESCRIPTION", "Enter a description of 1 to 500 characters.");
        if (!categories().contains(form.category()))
            throw ApiException.badRequest("UNKNOWN_CATEGORY", "Choose one of the existing categories.");
        String alt = images().stream().filter(i -> i.path().equals(form.imagePath())).map(SellerDtos.Image::alt)
                .findFirst().orElseThrow(() -> ApiException.badRequest("UNKNOWN_IMAGE", "Choose one of the existing catalogue images."));
        BigDecimal cap = jdbc.queryForObject("SELECT amount_cap FROM partners WHERE id=?", BigDecimal.class, partnerId);
        if (form.price().compareTo(cap) > 0)
            throw ApiException.badRequest("PRICE_ABOVE_CAP", "The price cannot be above your financing limit of " + cap.toPlainString() + " USD.");
        return new Valid(name, description, form.category(), form.imagePath(), alt, form.price().setScale(2, RoundingMode.UNNECESSARY));
    }
}
