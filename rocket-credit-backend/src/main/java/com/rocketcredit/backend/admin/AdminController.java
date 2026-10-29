// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.admin;

import com.rocketcredit.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Administrator area; SecurityConfig lets only ROLE_ADMIN in. Every write records an audit event in the
 * same transaction. The audit log has no write endpoint at all.
 *
 * <pre>
 *   GET  /api/admin/overview
 *   GET  /api/admin/users?role=&q=&page=&size=
 *   POST /api/admin/users/sellers                    {email,displayName,partnerId,password}
 *   POST /api/admin/users/{id}/disable | /enable
 *   POST /api/admin/users/{id}/reset-password        {password}
 *   GET  /api/admin/reviews?state=OPEN|DECIDED
 *   GET  /api/admin/reviews/{applicationId}
 *   POST /api/admin/reviews/{applicationId}/decision {outcome,approvedAmount?,note}
 *   GET  /api/admin/partners
 *   PUT  /api/admin/partners/{id}                    {displayName,amountCap,active}
 *   GET  /api/admin/audit?actor=&action=&from=&to=&page=&size=
 * </pre>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminAccountService accounts;
    private final ReviewService reviews;
    private final AdminPlatformService platform;

    public AdminController(AdminAccountService accounts, ReviewService reviews, AdminPlatformService platform) {
        this.accounts = accounts;
        this.reviews = reviews;
        this.platform = platform;
    }

    @GetMapping("/overview")
    public AdminDtos.Overview overview() {
        return platform.overview();
    }

    @GetMapping("/users")
    public AdminDtos.Page<AdminDtos.User> users(@RequestParam(required = false) String role, @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return accounts.list(role, q, page, size);
    }

    @PostMapping("/users/sellers")
    public ResponseEntity<AdminDtos.User> createSeller(@AuthenticationPrincipal AuthenticatedUser admin,
                                                       @Valid @RequestBody AdminDtos.CreateSeller body) {
        return ResponseEntity.status(201).body(accounts.createSeller(admin, body));
    }

    @PostMapping("/users/{id}/disable")
    public AdminDtos.User disable(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id) {
        return accounts.setEnabled(admin, id, false);
    }

    @PostMapping("/users/{id}/enable")
    public AdminDtos.User enable(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id) {
        return accounts.setEnabled(admin, id, true);
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                              @Valid @RequestBody AdminDtos.ResetPassword body) {
        accounts.resetPassword(admin, id, body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reviews")
    public AdminDtos.Page<AdminDtos.ReviewItem> reviews(@RequestParam(defaultValue = "OPEN") String state,
                                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return reviews.list(state, page, size);
    }

    @GetMapping("/reviews/{applicationId}")
    public AdminDtos.ReviewDetail review(@PathVariable long applicationId) {
        return reviews.get(applicationId);
    }

    @PostMapping("/reviews/{applicationId}/decision")
    public AdminDtos.ReviewDetail decide(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long applicationId,
                                         @Valid @RequestBody AdminDtos.DecideReview body) {
        return reviews.decide(admin, applicationId, body);
    }

    @GetMapping("/partners")
    public List<AdminDtos.Partner> partners() {
        return platform.partners();
    }

    @PutMapping("/partners/{id}")
    public AdminDtos.Partner updatePartner(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable long id,
                                           @Valid @RequestBody AdminDtos.UpdatePartner body) {
        return platform.updatePartner(admin, id, body);
    }

    @GetMapping("/audit")
    public AdminDtos.Page<AdminDtos.AuditEvent> audit(@RequestParam(required = false) String actor, @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return platform.audit(actor, action, from, to, page, size);
    }
}
