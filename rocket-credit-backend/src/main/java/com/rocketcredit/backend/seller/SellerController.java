// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.seller;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Seller area; SecurityConfig lets only ROLE_SELLER in. There is deliberately no partner path or query
 * parameter: the partner is always the one stored in the seller's session.
 *
 * <pre>
 *   GET  /api/seller/overview
 *   GET  /api/seller/orders?status=&from=&to=&page=&size=
 *   GET  /api/seller/products
 *   POST /api/seller/products               {name,description,category,imagePath,price}
 *   PUT  /api/seller/products/{id}           same body
 *   POST /api/seller/products/{id}/deactivate | /activate
 * </pre>
 */
@RestController
@RequestMapping("/api/seller")
public class SellerController {
    private final SellerService sellers;

    public SellerController(SellerService sellers) {
        this.sellers = sellers;
    }

    @GetMapping("/overview")
    public SellerDtos.Overview overview(@AuthenticationPrincipal AuthenticatedUser seller) {
        return sellers.overview(seller);
    }

    @GetMapping("/orders")
    public SellerDtos.Page<SellerDtos.Order> orders(@AuthenticationPrincipal AuthenticatedUser seller,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return sellers.orders(seller, status, from, to, page, size);
    }

    @GetMapping("/products")
    public SellerDtos.Catalogue products(@AuthenticationPrincipal AuthenticatedUser seller) {
        return sellers.catalogue(seller);
    }

    @PostMapping("/products")
    public ResponseEntity<SellerDtos.Product> create(@AuthenticationPrincipal AuthenticatedUser seller,
                                                     @Valid @RequestBody SellerDtos.ProductForm form) {
        return ResponseEntity.status(201).body(sellers.create(seller, form));
    }

    @PutMapping("/products/{id}")
    public SellerDtos.Product update(@AuthenticationPrincipal AuthenticatedUser seller, @PathVariable long id,
                                     @Valid @RequestBody SellerDtos.ProductForm form) {
        return sellers.update(seller, id, form);
    }

    @PostMapping("/products/{id}/deactivate")
    public SellerDtos.Product deactivate(@AuthenticationPrincipal AuthenticatedUser seller, @PathVariable long id) {
        return sellers.setActive(seller, id, false);
    }

    @PostMapping("/products/{id}/activate")
    public SellerDtos.Product activate(@AuthenticationPrincipal AuthenticatedUser seller, @PathVariable long id) {
        return sellers.setActive(seller, id, true);
    }
}
